/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix.ClassKind;
import com.intellij.java.impl.codeInsight.PackageUtil;
import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import com.intellij.java.impl.refactoring.ui.PackageNameReferenceEditorCombo;
import com.intellij.java.impl.refactoring.util.RefactoringMessageUtil;
import com.intellij.java.impl.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.java.language.psi.PsiNameHelper;
import consulo.application.util.function.Computable;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.ex.RecentsManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class CreateClassDialog extends DialogWrapper {
  private final JLabel myInformationLabel = new JLabel("#");
  private final JLabel myPackageLabel = new JLabel(CodeInsightLocalize.dialogCreateClassDestinationPackageLabel().get());
  private final ReferenceEditorComboWithBrowseButton myPackageComponent;
  private final JTextField myTfClassName = new MyTextField();
  private final Project myProject;
  private PsiDirectory myTargetDirectory;
  private final String myClassName;
  private final boolean myClassNameEditable;
  private final Module myModule;
  private final DestinationFolderComboBox myDestinationCB = new DestinationFolderComboBox() {
    @Override
    public String getTargetPackage() {
      return myPackageComponent.getText().trim();
    }

    @Override
    protected boolean reportBaseInTestSelectionInSource() {
      return CreateClassDialog.this.reportBaseInTestSelectionInSource();
    }

    @Override
    protected boolean reportBaseInSourceSelectionInTest() {
      return CreateClassDialog.this.reportBaseInSourceSelectionInTest();
    }
  };
  @NonNls private static final String RECENTS_KEY = "CreateClassDialog.RecentsKey";

  public CreateClassDialog(
    @Nonnull Project project,
    @Nonnull LocalizeValue title,
    @Nonnull String targetClassName,
    @Nonnull String targetPackageName,
    @Nonnull ClassKind kind,
    boolean classNameEditable,
    @Nullable Module defaultModule
  ) {
    super(project, true);
    myClassNameEditable = classNameEditable;
    myModule = defaultModule;
    myClassName = targetClassName;
    myProject = project;
    myPackageComponent = new PackageNameReferenceEditorCombo(
      targetPackageName,
      myProject,
      RECENTS_KEY,
      CodeInsightLocalize.dialogCreateClassPackageChooserTitle().get()
    );
    myPackageComponent.setTextFieldPreferredWidth(40);

    init();

    if (!myClassNameEditable) {
      setTitle(CodeInsightLocalize.dialogCreateClassName(StringUtil.capitalize(kind.getDescription()), targetClassName));
    }
    else {
      myInformationLabel.setText(CodeInsightLocalize.dialogCreateClassLabel(kind.getDescription()).get());
      setTitle(title);
    }

    myTfClassName.setText(myClassName);
    myDestinationCB.setData(myProject, getBaseDir(targetPackageName), s -> setErrorText(s), myPackageComponent.getChildComponent());
  }

  protected boolean reportBaseInTestSelectionInSource() {
    return false;
  }

  protected boolean reportBaseInSourceSelectionInTest() {
    return false;
  }

  @Nonnull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myClassNameEditable ? myTfClassName : myPackageComponent.getChildComponent();
  }

  @Override
  protected JComponent createCenterPanel() {
    return new JPanel(new BorderLayout());
  }

  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = JBUI.insets(4, 8);
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.anchor = GridBagConstraints.WEST;

    if (myClassNameEditable) {
      gbConstraints.weightx = 0;
      gbConstraints.gridwidth = 1;
      panel.add(myInformationLabel, gbConstraints);
      gbConstraints.insets = JBUI.insets(4, 8);
      gbConstraints.gridx = 1;
      gbConstraints.weightx = 1;
      gbConstraints.gridwidth = 1;
      gbConstraints.fill = GridBagConstraints.HORIZONTAL;
      gbConstraints.anchor = GridBagConstraints.WEST;
      panel.add(myTfClassName, gbConstraints);

      myTfClassName.getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          getOKAction().setEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(myTfClassName.getText()));
        }
      });
      getOKAction().setEnabled(StringUtil.isNotEmpty(myClassName));
    }

    gbConstraints.gridx = 0;
    gbConstraints.gridy = 2;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = 1;
    panel.add(myPackageLabel, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;

    new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myPackageComponent.getButton().doClick();
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)), myPackageComponent.getChildComponent());

    JPanel _panel = new JPanel(new BorderLayout());
    _panel.add(myPackageComponent, BorderLayout.CENTER);
    panel.add(_panel, gbConstraints);

    gbConstraints.gridy = 3;
    gbConstraints.gridx = 0;
    gbConstraints.gridwidth = 2;
    gbConstraints.insets.top = 12;
    gbConstraints.anchor = GridBagConstraints.WEST;
    gbConstraints.fill = GridBagConstraints.NONE;
    JBLabel label = new JBLabel(RefactoringLocalize.targetDestinationFolder().get());
    panel.add(label, gbConstraints);

    gbConstraints.gridy = 4;
    gbConstraints.gridx = 0;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.insets.top = 4;
    panel.add(myDestinationCB, gbConstraints);

    boolean isMultipleSourceRoots = ProjectRootManager.getInstance(myProject).getContentSourceRoots().length > 1;
    myDestinationCB.setVisible(isMultipleSourceRoots);
    label.setVisible(isMultipleSourceRoots);
    label.setLabelFor(myDestinationCB);
    return panel;
  }

  public PsiDirectory getTargetDirectory() {
    return myTargetDirectory;
  }

  private String getPackageName() {
    String name = myPackageComponent.getText();
    return name != null ? name.trim() : "";
  }

  private static class MyTextField extends JTextField {
    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      FontMetrics fontMetrics = getFontMetrics(getFont());
      size.width = fontMetrics.charWidth('a') * 40;
      return size;
    }
  }

  @Override
  protected void doOKAction() {
    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, myPackageComponent.getText());
    String packageName = getPackageName();

    String[] errorString = new String[1];
    CommandProcessor.getInstance().executeCommand(
      myProject,
      () -> {
        try {
          PackageWrapper targetPackage = new PackageWrapper(PsiManager.getInstance(myProject), packageName);
          MoveDestination destination = myDestinationCB.selectDirectory(targetPackage, false);
          if (destination == null) return;
          myTargetDirectory = myProject.getApplication()
            .runWriteAction((Computable<PsiDirectory>)() -> destination.getTargetDirectory(getBaseDir(packageName)));
          if (myTargetDirectory == null) {
            errorString[0] = ""; // message already reported by PackageUtil
            return;
          }
          errorString[0] = RefactoringMessageUtil.checkCanCreateClass(myTargetDirectory, getClassName());
        }
        catch (IncorrectOperationException e) {
          errorString[0] = e.getMessage();
        }
      },
      CodeInsightLocalize.createDirectoryCommand().get(),
      null
    );

    if (errorString[0] != null) {
      if (errorString[0].length() > 0) {
        Messages.showMessageDialog(myProject, errorString[0], CommonLocalize.titleError().get(), UIUtil.getErrorIcon());
      }
      return;
    }
    super.doOKAction();
  }

  @Nullable
  protected PsiDirectory getBaseDir(String packageName) {
    return myModule == null? null : PackageUtil.findPossiblePackageDirectoryInModule(myModule, packageName);
  }

  @Nonnull
  public String getClassName() {
    if (myClassNameEditable) {
      return myTfClassName.getText();
    }
    else {
      return myClassName;
    }
  }
}
