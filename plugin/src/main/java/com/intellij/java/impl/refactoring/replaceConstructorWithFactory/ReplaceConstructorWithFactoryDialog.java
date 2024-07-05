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
package com.intellij.java.impl.refactoring.replaceConstructorWithFactory;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import consulo.application.HelpManager;
import consulo.configurable.ConfigurationException;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import org.jetbrains.annotations.NonNls;
import com.intellij.java.language.util.TreeClassChooser;
import com.intellij.java.language.util.TreeClassChooserFactory;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiNameHelper;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.impl.refactoring.HelpID;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.ui.NameSuggestionsField;
import consulo.language.editor.refactoring.ui.RefactoringDialog;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import com.intellij.java.language.impl.ui.JavaReferenceEditorUtil;
import consulo.language.editor.ui.awt.ReferenceEditorWithBrowseButton;
import consulo.util.collection.ArrayUtil;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryDialog extends RefactoringDialog {
  private NameSuggestionsField myNameField;
  private final ReferenceEditorWithBrowseButton myTfTargetClassName;
  private JComboBox myTargetClassNameCombo;
  private final PsiClass myContainingClass;
  private final PsiMethod myConstructor;
  private final boolean myIsInner;
  private NameSuggestionsField.DataChanged myNameChangedListener;

  ReplaceConstructorWithFactoryDialog(Project project, PsiMethod constructor, PsiClass containingClass) {
    super(project, true);
    myContainingClass = containingClass;
    myConstructor = constructor;
    myIsInner = myContainingClass.getContainingClass() != null
                && !myContainingClass.hasModifierProperty(PsiModifier.STATIC);

    setTitle(ReplaceConstructorWithFactoryHandler.REFACTORING_NAME);

    myTfTargetClassName = JavaReferenceEditorUtil.createReferenceEditorWithBrowseButton(new ChooseClassAction(), "", project, true);

    init();
  }

  protected void dispose() {
    myNameField.removeDataChangedListener(myNameChangedListener);
    super.dispose();
  }

  public String getName() {
    return myNameField.getEnteredName();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField.getFocusableComponent();
  }

  public String getTargetClassName() {
    if (!myIsInner) {
      return myTfTargetClassName.getText();
    }
    else {
      return (String)myTargetClassNameCombo.getSelectedItem();
    }
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.BOTH;

    gbc.insets = new Insets(4, 0, 4, 8);
    gbc.gridwidth = 1;
    gbc.gridx = 0;
    gbc.gridy = 0;
    panel.add(new JLabel(RefactoringLocalize.factoryMethodNameLabel().get()), gbc);

    gbc.gridx++;
    gbc.weightx = 1.0;
    @NonNls final String[] nameSuggestions = new String[]{
      "create" + myContainingClass.getName(),
      "new" + myContainingClass.getName(),
      "getInstance",
      "newInstance"
      };
    myNameField = new NameSuggestionsField(nameSuggestions, getProject());
    myNameChangedListener = new NameSuggestionsField.DataChanged() {
      public void dataChanged() {
        validateButtons();
      }
    };
    myNameField.addDataChangedListener(myNameChangedListener);
    panel.add(myNameField.getComponent(), gbc);

    JPanel targetClassPanel = createTargetPanel();

    gbc.gridx = 0;
    gbc.gridy++;
    gbc.gridwidth = 2;
    panel.add(targetClassPanel, gbc);


    return panel;

  }

  private JPanel createTargetPanel() {
    JPanel targetClassPanel = new JPanel(new BorderLayout());
    if (!myIsInner) {
      JLabel label = new JLabel(RefactoringLocalize.replaceConstructorWithFactoryTargetFqName().get());
      label.setLabelFor(myTfTargetClassName);
      targetClassPanel.add(label, BorderLayout.NORTH);
      targetClassPanel.add(myTfTargetClassName, BorderLayout.CENTER);
      myTfTargetClassName.setText(myContainingClass.getQualifiedName());
    }
    else {
      ArrayList<String> list = new ArrayList<String>();
      PsiElement parent = myContainingClass;
      while (parent instanceof PsiClass) {
        list.add(((PsiClass)parent).getQualifiedName());
        parent = parent.getParent();
      }

      myTargetClassNameCombo = new JComboBox(ArrayUtil.toStringArray(list));
      JLabel label = new JLabel(RefactoringLocalize.replaceConstructorWithFactoryTargetFqName().get());
      label.setLabelFor(myTargetClassNameCombo.getEditor().getEditorComponent());
      targetClassPanel.add(label, BorderLayout.NORTH);
      targetClassPanel.add(myTargetClassNameCombo, BorderLayout.CENTER);
    }
    return targetClassPanel;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryDialog";
  }

  private class ChooseClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(getProject())
        .createProjectScopeChooser(RefactoringLocalize.chooseDestinationClass().get());
      chooser.selectDirectory(myContainingClass.getContainingFile().getContainingDirectory());
      chooser.showDialog();
      PsiClass aClass = chooser.getSelected();
      if (aClass != null) {
        myTfTargetClassName.setText(aClass.getQualifiedName());
      }
    }
  }


  protected JComponent createCenterPanel() {
    return null;
  }

  protected void doAction() {
    final Project project = getProject();
    final PsiManager manager = PsiManager.getInstance(project);
    final String targetClassName = getTargetClassName();
    final PsiClass targetClass =
      JavaPsiFacade.getInstance(manager.getProject()).findClass(targetClassName, GlobalSearchScope.allScope(project));
    if (targetClass == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringLocalize.class0NotFound(targetClassName).get());
      CommonRefactoringUtil.showErrorMessage(ReplaceConstructorWithFactoryHandler.REFACTORING_NAME,
                                              message, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY, project);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, targetClass)) return;

    invokeRefactoring(new ReplaceConstructorWithFactoryProcessor(project, myConstructor, myContainingClass,
                                                                 targetClass, getName()));
  }


  @Override
  protected void canRun() throws ConfigurationException {
    final String name = myNameField.getEnteredName();
    final PsiNameHelper nameHelper = PsiNameHelper.getInstance(myContainingClass.getProject());
    if (!nameHelper.isIdentifier(name)) {
      throw new ConfigurationException("\'" + name + "\' is invalid factory method name");
    }
  }
}
