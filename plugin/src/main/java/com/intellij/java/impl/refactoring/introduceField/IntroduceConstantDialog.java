/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.introduceField;

import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.introduceParameter.AbstractJavaInplaceIntroducer;
import com.intellij.java.impl.refactoring.ui.*;
import com.intellij.java.impl.refactoring.util.EnumConstantsUtil;
import com.intellij.java.impl.refactoring.util.RefactoringMessageUtil;
import com.intellij.java.impl.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.application.HelpManager;
import consulo.document.event.DocumentAdapter;
import consulo.document.event.DocumentEvent;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.editor.refactoring.ui.NameSuggestionsField;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.RecentsManager;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.StateRestoringCheckBox;
import consulo.ui.ex.awt.UIUtil;
import consulo.usage.UsageViewUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

class IntroduceConstantDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(IntroduceConstantDialog.class);
  @NonNls private static final String RECENTS_KEY = "IntroduceConstantDialog.RECENTS_KEY";
  @NonNls protected static final String NONNLS_SELECTED_PROPERTY = "INTRODUCE_CONSTANT_NONNLS";

  private final Project myProject;
  private final PsiClass myParentClass;
  private final PsiExpression myInitializerExpression;
  private final PsiLocalVariable myLocalVariable;
  private final boolean myInvokedOnDeclaration;
  private final PsiExpression[] myOccurrences;
  private final String myEnteredName;
  private final int myOccurrencesCount;
  private PsiClass myTargetClass;
  private final TypeSelectorManager myTypeSelectorManager;

  private NameSuggestionsField myNameField;
  private JCheckBox myCbReplaceAll;

  private TypeSelector myTypeSelector;
  private StateRestoringCheckBox myCbDeleteVariable;
  private final JavaCodeStyleManager myCodeStyleManager;
  private ReferenceEditorComboWithBrowseButton myTfTargetClassName;
  private BaseExpressionToFieldHandler.TargetDestination myDestinationClass;
  private JPanel myTypePanel;
  private JPanel myTargetClassNamePanel;
  private JPanel myPanel;
  private JLabel myTypeLabel;
  private JPanel myNameSuggestionPanel;
  private JLabel myNameSuggestionLabel;
  private JLabel myTargetClassNameLabel;
  private JCheckBox myCbNonNls;
  private JPanel myVisibilityPanel;
  private final JavaVisibilityPanel myVPanel;
  private final JCheckBox myIntroduceEnumConstantCb = new JCheckBox(RefactoringBundle.message("introduce.constant.enum.cb"), true);

  IntroduceConstantDialog(
    Project project,
    PsiClass parentClass,
    PsiExpression initializerExpression,
    PsiLocalVariable localVariable,
    boolean isInvokedOnDeclaration,
    PsiExpression[] occurrences,
    PsiClass targetClass,
    TypeSelectorManager typeSelectorManager, String enteredName
  ) {
    super(project, true);
    myProject = project;
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myLocalVariable = localVariable;
    myInvokedOnDeclaration = isInvokedOnDeclaration;
    myOccurrences = occurrences;
    myEnteredName = enteredName;
    myOccurrencesCount = occurrences.length;
    myTargetClass = targetClass;
    myTypeSelectorManager = typeSelectorManager;
    myDestinationClass = null;

    setTitle(IntroduceConstantHandlerImpl.REFACTORING_NAME);
    myCodeStyleManager = JavaCodeStyleManager.getInstance(myProject);
    myVPanel = new JavaVisibilityPanel(false, true);
    myVisibilityPanel.add(myVPanel, BorderLayout.CENTER);
    init();

    myVPanel.setVisibility(JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY);
    myIntroduceEnumConstantCb.setEnabled(isSuitableForEnumConstant());
    updateVisibilityPanel();
    updateButtons();
  }

  public String getEnteredName() {
    return myNameField.getEnteredName();
  }

  private String getTargetClassName() {
    return myTfTargetClassName.getText().trim();
  }

  public BaseExpressionToFieldHandler.TargetDestination getDestinationClass () {
    return myDestinationClass;
  }

  public boolean introduceEnumConstant() {
    return myIntroduceEnumConstantCb.isEnabled() && myIntroduceEnumConstantCb.isSelected();
  }

  public String getFieldVisibility() {
    return myVPanel.getVisibility();
  }

  public boolean isReplaceAllOccurrences() {
    return myOccurrencesCount > 1 && myCbReplaceAll.isSelected();
  }

  public PsiType getSelectedType() {
    return myTypeSelector.getSelectedType();
  }

  @Nonnull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_CONSTANT);
  }

  protected JComponent createNorthPanel() {
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    myTypePanel.setLayout(new BorderLayout());
    myTypePanel.add(myTypeSelector.getComponent(), BorderLayout.CENTER);
    if (myTypeSelector.getFocusableComponent() != null) {
      myTypeLabel.setLabelFor(myTypeSelector.getFocusableComponent());
    }

    myNameField = new NameSuggestionsField(myProject);
    myNameSuggestionPanel.setLayout(new BorderLayout());
    myNameField.addDataChangedListener(this::updateButtons);
    myNameSuggestionPanel.add(myNameField.getComponent(), BorderLayout.CENTER);
    myNameSuggestionLabel.setLabelFor(myNameField.getFocusableComponent());

    Set<String> possibleClassNames = new LinkedHashSet<>();
    for (final PsiExpression occurrence : myOccurrences) {
      final PsiClass parentClass = new IntroduceConstantHandlerImpl().getParentClass(occurrence);
      if (parentClass != null && parentClass.getQualifiedName() != null) {
        possibleClassNames.add(parentClass.getQualifiedName());
      }
    }
    myTfTargetClassName =
      new ReferenceEditorComboWithBrowseButton(new ChooseClassAction(), "", myProject, true, RECENTS_KEY);
    myTargetClassNamePanel.setLayout(new BorderLayout());
    myTargetClassNamePanel.add(myTfTargetClassName, BorderLayout.CENTER);
    myTargetClassNameLabel.setLabelFor(myTfTargetClassName);
    for (String possibleClassName : possibleClassNames) {
      myTfTargetClassName.prependItem(possibleClassName);
    }
    myTfTargetClassName.getChildComponent().addDocumentListener(new DocumentAdapter() {
      public void documentChanged(DocumentEvent e) {
        targetClassChanged();
        enableEnumDependant(introduceEnumConstant());
      }
    });
    myIntroduceEnumConstantCb.addActionListener(e -> enableEnumDependant(introduceEnumConstant()));
    final JPanel enumPanel = new JPanel(new BorderLayout());
    enumPanel.add(myIntroduceEnumConstantCb, BorderLayout.EAST);
    myTargetClassNamePanel.add(enumPanel, BorderLayout.SOUTH);

    final String propertyName;
    if (myLocalVariable != null) {
      propertyName = myCodeStyleManager.variableNameToPropertyName(myLocalVariable.getName(), VariableKind.LOCAL_VARIABLE);
    }
    else {
      propertyName = null;
    }
    final NameSuggestionsManager nameSuggestionsManager =
      new NameSuggestionsManager(myTypeSelector, myNameField, createNameSuggestionGenerator(propertyName, myInitializerExpression,
                                                                                            myCodeStyleManager, myEnteredName, myParentClass));

    nameSuggestionsManager.setLabelsFor(myTypeLabel, myNameSuggestionLabel);
    //////////
    if (myOccurrencesCount > 1) {
      myCbReplaceAll.addItemListener(e -> {
        updateTypeSelector();

        myNameField.requestFocusInWindow();
      });
      myCbReplaceAll.setText(RefactoringBundle.message("replace.all.occurences", myOccurrencesCount));
    }
    else {
      myCbReplaceAll.setVisible(false);
    }

    if (myLocalVariable != null) {
      if (myInvokedOnDeclaration) {
        myCbDeleteVariable.setEnabled(false);
        myCbDeleteVariable.setSelected(true);
      }
      else if (myCbReplaceAll != null) {
        updateCbDeleteVariable();
        myCbReplaceAll.addItemListener(e -> updateCbDeleteVariable());
      }
    }
    else {
      myCbDeleteVariable.setVisible(false);
    }

    final PsiManager psiManager = PsiManager.getInstance(myProject);
    if (
      (
        myTypeSelectorManager.isSuggestedType(JavaClassNames.JAVA_LANG_STRING)
          || (myLocalVariable != null && AnnotationUtil.isAnnotated(myLocalVariable, AnnotationUtil.NON_NLS, false, false))
      )
      && PsiUtil.isLanguageLevel5OrHigher(myParentClass)
      && JavaPsiFacade.getInstance(psiManager.getProject()).findClass(AnnotationUtil.NON_NLS, myParentClass.getResolveScope()) != null
    ) {
      final PropertiesComponent component = PropertiesComponent.getInstance(myProject);
      myCbNonNls.setSelected(component.isTrueValue(NONNLS_SELECTED_PROPERTY));
      myCbNonNls.addItemListener(e -> component.setValue(NONNLS_SELECTED_PROPERTY, Boolean.toString(myCbNonNls.isSelected())));
    } else {
      myCbNonNls.setVisible(false);
    }

    updateTypeSelector();

    enableEnumDependant(introduceEnumConstant());
    return myPanel;
  }

  public void setReplaceAllOccurrences(boolean replaceAllOccurrences) {
    if (myCbReplaceAll != null) {
      myCbReplaceAll.setSelected(replaceAllOccurrences);
    }
  }

  protected static NameSuggestionsGenerator createNameSuggestionGenerator(
    final String propertyName,
    final PsiExpression psiExpression,
    final JavaCodeStyleManager codeStyleManager,
    final String enteredName, final PsiClass parentClass
  ) {
    return type -> {
      SuggestedNameInfo nameInfo =
          codeStyleManager.suggestVariableName(VariableKind.STATIC_FINAL_FIELD, propertyName, psiExpression, type);
      if (psiExpression != null) {
        String[] names = nameInfo.names;
        for (int i = 0, namesLength = names.length; i < namesLength; i++) {
          String name = names[i];
          if (parentClass.findFieldByName(name, false) != null) {
            names[i] = codeStyleManager.suggestUniqueVariableName(name, psiExpression, true);
          }
        }
      }
      final String[] strings = AbstractJavaInplaceIntroducer.appendUnresolvedExprName(
        JavaCompletionUtil.completeVariableNameForRefactoring(codeStyleManager, type, VariableKind.LOCAL_VARIABLE, nameInfo),
        psiExpression
      );
      return new SuggestedNameInfo.Delegate(enteredName != null ? ArrayUtil.mergeArrays(new String[]{enteredName}, strings): strings, nameInfo);
    };
  }

  private void updateButtons() {
    setOKActionEnabled(PsiNameHelper.getInstance(myProject).isIdentifier(getEnteredName()));
  }

  private void targetClassChanged() {
    final String targetClassName = getTargetClassName();
    myTargetClass = JavaPsiFacade.getInstance(myProject).findClass(targetClassName, GlobalSearchScope.projectScope(myProject));
    updateVisibilityPanel();
    myIntroduceEnumConstantCb.setEnabled(isSuitableForEnumConstant());
  }

  private boolean isSuitableForEnumConstant() {
    return EnumConstantsUtil.isSuitableForEnumConstant(getSelectedType(), myTargetClass) && PsiTreeUtil
                                                                                              .getParentOfType(myInitializerExpression,
                                                                                                               PsiEnumConstant.class) == null;
  }

  private void enableEnumDependant(boolean enable) {
    if (enable) {
      myVPanel.disableAllButPublic();
    } else {
      updateVisibilityPanel();
    }
    myCbNonNls.setEnabled(!enable);
  }

  protected JComponent createCenterPanel() {
    return new JPanel();
  }

  public boolean isDeleteVariable() {
    return myInvokedOnDeclaration || myCbDeleteVariable != null && myCbDeleteVariable.isSelected();
  }

  public boolean isAnnotateAsNonNls() {
    return myCbNonNls != null && myCbNonNls.isSelected();
  }

  private void updateCbDeleteVariable() {
    if (!myCbReplaceAll.isSelected()) {
      myCbDeleteVariable.makeUnselectable(false);
    }
    else {
      myCbDeleteVariable.makeSelectable();
    }
  }

  private void updateTypeSelector() {
    if (myCbReplaceAll != null) {
      myTypeSelectorManager.setAllOccurrences(myCbReplaceAll.isSelected());
    }
    else {
      myTypeSelectorManager.setAllOccurrences(false);
    }
  }

  private void updateVisibilityPanel() {
    if (myTargetClass != null && myTargetClass.isInterface()) {
      myVPanel.disableAllButPublic();
    }
    else {
      UIUtil.setEnabled(myVisibilityPanel, true, true);
      // exclude all modifiers not visible from all occurences
      final Set<String> visible = new HashSet<>();
      visible.add(PsiModifier.PRIVATE);
      visible.add(PsiModifier.PROTECTED);
      visible.add(PsiModifier.PACKAGE_LOCAL);
      visible.add(PsiModifier.PUBLIC);
      for (PsiExpression occurrence : myOccurrences) {
        final PsiManager psiManager = PsiManager.getInstance(myProject);
        for (Iterator<String> iterator = visible.iterator(); iterator.hasNext();) {
          String modifier = iterator.next();

          try {
            final String modifierText = PsiModifier.PACKAGE_LOCAL.equals(modifier) ? "" : modifier + " ";
            final PsiField field = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createFieldFromText(modifierText + "int xxx;", myTargetClass);
            if (!JavaResolveUtil.isAccessible(field, myTargetClass, field.getModifierList(), occurrence, myTargetClass, null)) {
              iterator.remove();
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
      if (!visible.contains(getFieldVisibility())) {
        if (visible.contains(PsiModifier.PUBLIC)) myVPanel.setVisibility(PsiModifier.PUBLIC);
        if (visible.contains(PsiModifier.PACKAGE_LOCAL)) myVPanel.setVisibility(PsiModifier.PACKAGE_LOCAL);
        if (visible.contains(PsiModifier.PROTECTED)) myVPanel.setVisibility(PsiModifier.PROTECTED);
        if (visible.contains(PsiModifier.PRIVATE)) myVPanel.setVisibility(PsiModifier.PRIVATE);
      }
    }
  }

  @RequiredUIAccess
  protected void doOKAction() {
    final String targetClassName = getTargetClassName();
    PsiClass newClass = myParentClass;

    if (!"".equals (targetClassName) && !Comparing.strEqual(targetClassName, myParentClass.getQualifiedName())) {
      newClass = JavaPsiFacade.getInstance(myProject).findClass(targetClassName, GlobalSearchScope.projectScope(myProject));
      if (newClass == null) {
        if (Messages.showOkCancelDialog(
          myProject,
          RefactoringBundle.message("class.does.not.exist.in.the.project"),
          IntroduceConstantHandlerImpl.REFACTORING_NAME,
          UIUtil.getErrorIcon()
        ) != OK_EXIT_CODE) {
          return;
        }
        myDestinationClass = new BaseExpressionToFieldHandler.TargetDestination(targetClassName, myParentClass);
      } else {
        myDestinationClass = new BaseExpressionToFieldHandler.TargetDestination(newClass);
      }
    }

    String fieldName = getEnteredName();
    String errorString = null;
    if ("".equals(fieldName)) {
      errorString = RefactoringBundle.message("no.field.name.specified");
    } else if (!PsiNameHelper.getInstance(myProject).isIdentifier(fieldName)) {
      errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(fieldName);
    } else if (newClass != null && !myParentClass.getLanguage().equals(newClass.getLanguage())) {
      errorString = RefactoringBundle.message("move.to.different.language", UsageViewUtil.getType(myParentClass),
                                              myParentClass.getQualifiedName(), newClass.getQualifiedName());
    }
    if (errorString != null) {
      CommonRefactoringUtil.showErrorMessage(
              IntroduceFieldHandler.REFACTORING_NAME,
              errorString,
              HelpID.INTRODUCE_FIELD,
              myProject);
      return;
    }
    if (newClass != null) {
      PsiField oldField = newClass.findFieldByName(fieldName, true);

      if (oldField != null) {
        int answer = Messages.showYesNoDialog(
          myProject,
          RefactoringBundle.message("field.exists", fieldName, oldField.getContainingClass().getQualifiedName()),
          IntroduceFieldHandler.REFACTORING_NAME,
          UIUtil.getWarningIcon()
        );
        if (answer != 0) {
          return;
        }
      }
    }

    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = getFieldVisibility();

    RecentsManager.getInstance(myProject).registerRecentEntry(RECENTS_KEY, targetClassName);
    super.doOKAction();
  }

  @RequiredUIAccess
  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }

  private class ChooseClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser(
        RefactoringBundle.message("choose.destination.class"),
        GlobalSearchScope.projectScope(myProject),
        aClass -> aClass.getParent() instanceof PsiJavaFile || aClass.hasModifierProperty(PsiModifier.STATIC),
        null
      );
      if (myTargetClass != null) {
        chooser.selectDirectory(myTargetClass.getContainingFile().getContainingDirectory());
      }
      chooser.showDialog();
      PsiClass aClass = chooser.getSelected();
      if (aClass != null) {
        myTfTargetClassName.setText(aClass.getQualifiedName());
      }
    }
  }
}
