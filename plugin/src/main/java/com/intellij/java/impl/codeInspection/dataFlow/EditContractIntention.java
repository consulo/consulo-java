// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInsight.DefaultInferredAnnotationProvider;
import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.MutationSignature;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.java.impl.codeInsight.ExternalAnnotationsManagerImpl;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.ExternalAnnotationsManager;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.Collections;

/**
 * @author peter
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "id.EditContractIntention", fileExtensions = "java", categories = {"Java", "Annotations"})
public class EditContractIntention extends BaseIntentionAction implements LowPriorityAction {
  private static final String ourPrompt = "<html>Please specify the contract text<p>" +
    "Example: <code>_, null -> false</code><br>" +
    "<small>See intention action description for more details</small></html>";

  public EditContractIntention() {
    setText(LocalizeValue.localizeTODO("Edit method contract"));
  }

  @Nullable
  private static PsiMethod getTargetMethod(Editor editor, PsiFile file) {
    final PsiModifierListOwner owner = AddAnnotationPsiFix.getContainer(file, editor.getCaretModel().getOffset(), true);
    if (owner instanceof PsiMethod && ExternalAnnotationsManagerImpl.areExternalAnnotationsApplicable(owner)) {
      PsiElement original = owner.getOriginalElement();
      return original instanceof PsiMethod ? (PsiMethod)original : (PsiMethod)owner;
    }
    return null;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    final PsiMethod method = getTargetMethod(editor, file);
    if (method != null) {
      boolean hasContract = JavaMethodContractUtil.findContractAnnotation(method) != null;
      setText(LocalizeValue.localizeTODO(hasContract ? "Edit method contract of '" + method.getName() + "'" : "Add method contract to '" + method.getName() + "'"));
      return true;
    }
    return false;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiMethod method = getTargetMethod(editor, file);
    assert method != null;
    PsiAnnotation existingAnno = AnnotationUtil.findAnnotationInHierarchy(method, Collections.singleton(Contract.class.getName()));
    String oldContract = existingAnno == null ? null : AnnotationUtil.getStringAttributeValue(existingAnno, "value");
    boolean oldPure = existingAnno != null && Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(existingAnno, "pure"));
    String oldMutates = existingAnno == null ? null : AnnotationUtil.getStringAttributeValue(existingAnno, "mutates");

    JBTextField contractText = new JBTextField(oldContract);
    JBTextField mutatesText = new JBTextField(oldMutates);
    JCheckBox pureCB = createPureCheckBox(oldPure);
    DialogBuilder builder = createDialog(project, contractText, pureCB, mutatesText);
    DocumentAdapter validator = new DocumentAdapter() {
      @Override
      protected void textChanged(@Nonnull DocumentEvent e) {
        String contractError = getContractErrorMessage(contractText.getText(), method);
        if (contractError != null) {
          builder.setOkActionEnabled(false);
          builder.setErrorText(contractError, contractText);
        }
        else {
          String mutatesError = getMutatesErrorMessage(mutatesText.getText(), method);
          if (mutatesError != null) {
            builder.setOkActionEnabled(false);
            builder.setErrorText(mutatesError, mutatesText);
          }
          else {
            builder.setOkActionEnabled(true);
            builder.setErrorText(null);
          }
        }
      }
    };
    Runnable updateControls = () -> {
      if (pureCB.isSelected()) {
        mutatesText.setText("");
        mutatesText.setEnabled(false);
      }
      else {
        mutatesText.setEnabled(true);
      }
    };
    pureCB.addChangeListener(e -> updateControls.run());
    contractText.getDocument().addDocumentListener(validator);
    mutatesText.getDocument().addDocumentListener(validator);
    updateControls.run();
    if (builder.showAndGet()) {
      updateContract(method, contractText.getText(), pureCB.isSelected(), mutatesText.getText());
    }
  }

  private static DialogBuilder createDialog(@Nonnull Project project,
                                            JBTextField contractText,
                                            JCheckBox pureCB,
                                            JBTextField mutatesText) {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints constraints =
      new GridBagConstraints(0, 0, 2, 1, 4.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, JBUI.insets(2), 0, 0);
    panel.add(Messages.configureMessagePaneUi(new JTextPane(), ourPrompt), constraints);
    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.gridwidth = 1;
    constraints.weightx = 1;
    JLabel contractLabel = new JLabel("Contract:");
    contractLabel.setDisplayedMnemonic('c');
    contractLabel.setLabelFor(contractText);
    panel.add(contractLabel, constraints);
    constraints.gridx = 1;
    constraints.weightx = 3;
    panel.add(contractText, constraints);
    constraints.gridx = 0;
    constraints.gridy = 2;
    constraints.gridwidth = 2;
    constraints.weightx = 4;
    panel.add(pureCB, constraints);
    panel.add(pureCB, constraints);
    if (ApplicationManager.getApplication().isInternal()) {
      constraints.gridx = 0;
      constraints.gridy = 3;
      constraints.weightx = 1;
      constraints.gridwidth = 1;
      JLabel mutatesLabel = new JLabel("Mutates:");
      mutatesLabel.setDisplayedMnemonic('m');
      mutatesLabel.setLabelFor(mutatesText);
      panel.add(mutatesLabel, constraints);
      constraints.gridx = 1;
      constraints.weightx = 3;
      panel.add(mutatesText, constraints);
    }

    DialogBuilder builder = new DialogBuilder(project).setNorthPanel(panel).title("Edit Method Contract");
    builder.setPreferredFocusComponent(contractText);
    builder.setHelpId("define_contract_dialog");
    return builder;
  }

  private static JCheckBox createPureCheckBox(boolean selected) {
    JCheckBox pureCB = new NonFocusableCheckBox("Method is pure (has no side effects)");
    pureCB.setMnemonic('p');
    pureCB.setSelected(selected);
    return pureCB;
  }

  private static void updateContract(PsiMethod method, String contract, boolean pure, String mutates) {
    Project project = method.getProject();
    ExternalAnnotationsManager manager = ExternalAnnotationsManager.getInstance(project);
    manager.deannotate(method, JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT);
    PsiAnnotation mockAnno = DefaultInferredAnnotationProvider.createContractAnnotation(project, pure, contract, mutates);
    if (mockAnno != null) {
      try {
        manager.annotateExternally(method, JavaMethodContractUtil.ORG_JETBRAINS_ANNOTATIONS_CONTRACT, method.getContainingFile(),
                                   mockAnno.getParameterList().getAttributes());
      }
      catch (ExternalAnnotationsManager.CanceledConfigurationException ignored) {
      }
    }
    DaemonCodeAnalyzer.getInstance(project).restart();
  }

  @Nullable
  private static String getMutatesErrorMessage(String mutates, PsiMethod method) {
    return StringUtil.isEmpty(mutates) ? null : MutationSignature.checkSignature(mutates, method);
  }

  @Nullable
  private static String getContractErrorMessage(String contract, PsiMethod method) {
    if (StringUtil.isEmpty(contract)) {
      return null;
    }
    StandardMethodContract.ParseException error = ContractInspection.checkContract(method, contract);
    return error != null ? error.getMessage() : null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
