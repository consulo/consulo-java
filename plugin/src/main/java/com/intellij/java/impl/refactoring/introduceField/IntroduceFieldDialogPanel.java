/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.ui.JavaVisibilityPanel;
import com.intellij.java.impl.refactoring.ui.TypeSelectorManager;
import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awtUnsafe.TargetAWT;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemListener;

/**
 * User: anna
 * Date: 4/8/11
 */
public class IntroduceFieldDialogPanel extends IntroduceFieldCentralPanel {
  private JRadioButton myRbInConstructor;
  private JRadioButton myRbInCurrentMethod;
  private JRadioButton myRbInFieldDeclaration;
  private JRadioButton myRbInSetUp;
  private JavaVisibilityPanel myVisibilityPanel;

  public IntroduceFieldDialogPanel(PsiClass parentClass,
                                   PsiExpression initializerExpression,
                                   PsiLocalVariable localVariable,
                                   boolean isCurrentMethodConstructor,
                                   boolean isInvokedOnDeclaration,
                                   boolean willBeDeclaredStatic,
                                   PsiExpression[] occurrences,
                                   boolean allowInitInMethod,
                                   boolean allowInitInMethodIfAll,
                                   TypeSelectorManager typeSelectorManager) {
    super(parentClass, initializerExpression, localVariable, isCurrentMethodConstructor, isInvokedOnDeclaration, willBeDeclaredStatic,
          occurrences, allowInitInMethod, allowInitInMethodIfAll, typeSelectorManager);
  }

  protected void initializeControls(PsiExpression initializerExpression, BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace) {
    initializeInitializerPlace(initializerExpression, ourLastInitializerPlace);
    String ourLastVisibility = JavaRefactoringSettings.getInstance().INTRODUCE_FIELD_VISIBILITY;
    myVisibilityPanel.setVisibility(ourLastVisibility);
    super.initializeControls(initializerExpression, ourLastInitializerPlace);
  }

  protected void initializeInitializerPlace(PsiExpression initializerExpression,
                                            BaseExpressionToFieldHandler.InitializationPlace ourLastInitializerPlace) {
    if (initializerExpression != null) {
      setEnabledInitializationPlaces(initializerExpression, initializerExpression);
      if (!myAllowInitInMethod) {
        myRbInCurrentMethod.setEnabled(false);
      }
    } else {
      myRbInConstructor.setEnabled(false);
      myRbInCurrentMethod.setEnabled(false);
      myRbInFieldDeclaration.setEnabled(false);
      if (myRbInSetUp != null) myRbInSetUp.setEnabled(false);
    }

    final PsiMethod setUpMethod = TestFrameworks.getInstance().findSetUpMethod(myParentClass);
    if (myInitializerExpression != null && PsiTreeUtil.isAncestor(setUpMethod, myInitializerExpression, false) && myRbInSetUp.isEnabled() ||
        ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD && TestFrameworks.getInstance().isTestClass(myParentClass) && myRbInSetUp.isEnabled()) {
      myRbInSetUp.setSelected(true);
    }
    else if (ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR) {
      if (myRbInConstructor.isEnabled()) {
        myRbInConstructor.setSelected(true);
      } else {
        selectInCurrentMethod();
      }
    } else if (ourLastInitializerPlace == BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION) {
      if (myRbInFieldDeclaration.isEnabled()) {
        myRbInFieldDeclaration.setSelected(true);
      } else {
        selectInCurrentMethod();
      }
    } else {
      selectInCurrentMethod();
    }
  }

  private void selectInCurrentMethod() {
    if (myRbInCurrentMethod.isEnabled()) {
      myRbInCurrentMethod.setSelected(true);
    }
    else if (myRbInFieldDeclaration.isEnabled()) {
      myRbInFieldDeclaration.setSelected(true);
    }
    else {
      myRbInCurrentMethod.setSelected(true);
    }
  }

  public BaseExpressionToFieldHandler.InitializationPlace getInitializerPlace() {
    if (myRbInConstructor.isSelected()) {
      return BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR;
    }
    if (myRbInCurrentMethod.isSelected()) {
      return BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD;
    }
    if (myRbInFieldDeclaration.isSelected()) {
      return BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION;
    }
    if (myRbInSetUp != null && myRbInSetUp.isSelected()) {
      return BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD;
    }

    LOG.assertTrue(false);
    return BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION;
  }

  public String getFieldVisibility() {
    return myVisibilityPanel.getVisibility();
  }

  protected JComponent createInitializerPlacePanel(ItemListener itemListener, ItemListener finalUpdater) {
    JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new BorderLayout());

    JPanel initializationPanel = new JPanel();
    initializationPanel.setBorder(IdeBorderFactory.createTitledBorder(RefactoringLocalize.initializeInBorderTitle().get(), true));
    initializationPanel.setLayout(new BoxLayout(initializationPanel, BoxLayout.Y_AXIS));

    myRbInCurrentMethod = new JRadioButton();
    myRbInCurrentMethod.setFocusable(false);
    myRbInCurrentMethod.setText(RefactoringLocalize.currentMethodRadio().get());
    myRbInCurrentMethod.setEnabled(myAllowInitInMethod);

    myRbInFieldDeclaration = new JRadioButton();
    myRbInFieldDeclaration.setFocusable(false);
    myRbInFieldDeclaration.setText(RefactoringLocalize.fieldDeclarationRadio().get());

    myRbInConstructor = new JRadioButton();
    myRbInConstructor.setFocusable(false);
    myRbInConstructor.setText(RefactoringLocalize.classConstructorsRadio().get());



    initializationPanel.add(myRbInCurrentMethod);
    initializationPanel.add(myRbInFieldDeclaration);
    initializationPanel.add(myRbInConstructor);

    if (TestFrameworks.getInstance().isTestClass(myParentClass)) {
      myRbInSetUp = new JRadioButton();
      myRbInSetUp.setFocusable(false);
      myRbInSetUp.setText(RefactoringLocalize.setupMethodRadio().get());
      initializationPanel.add(myRbInSetUp);
    }

    ButtonGroup bg = new ButtonGroup();
    bg.add(myRbInCurrentMethod);
    bg.add(myRbInFieldDeclaration);
    bg.add(myRbInConstructor);
    if (myRbInSetUp != null) bg.add(myRbInSetUp);

    myRbInConstructor.addItemListener(itemListener);
    myRbInCurrentMethod.addItemListener(itemListener);
    myRbInFieldDeclaration.addItemListener(itemListener);
    myRbInConstructor.addItemListener(finalUpdater);
    myRbInCurrentMethod.addItemListener(finalUpdater);
    myRbInFieldDeclaration.addItemListener(finalUpdater);
    if (myRbInSetUp != null) myRbInSetUp.addItemListener(finalUpdater);

//    modifiersPanel.add(myCbFinal);
//    modifiersPanel.add(myCbStatic);

    JPanel groupPanel = new JPanel(new GridLayout(1, 2));
    groupPanel.add(initializationPanel);

    myVisibilityPanel = new JavaVisibilityPanel(false, false);
    groupPanel.add(TargetAWT.to(myVisibilityPanel.getComponent()));

    mainPanel.add(groupPanel, BorderLayout.CENTER);

    return mainPanel;
  }

  @Override
  protected boolean updateInitializationPlaceModel(boolean initializedInSetup) {
    myRbInFieldDeclaration.setEnabled(false);
    myRbInConstructor.setEnabled(false);
    enableFinal(false);
    if (myRbInSetUp != null){
      if (!initializedInSetup) {
        myRbInSetUp.setEnabled(false);
      } else {
        return true;
      }
    }
    return false;
  }

  @Override
  protected boolean hasSetUpChoice() {
    return myRbInSetUp != null;
  }

  public void setInitializeInFieldDeclaration() {
    myRbInFieldDeclaration.setSelected(true);
  }

  public void setVisibility(String visibility) {
    myVisibilityPanel.setVisibility(visibility);
  }

  @Override
  protected boolean allowFinal() {
    boolean allowFinal = myRbInFieldDeclaration.isSelected() || (myRbInConstructor.isSelected() && !myWillBeDeclaredStatic);
    if (myRbInCurrentMethod.isSelected() && myIsCurrentMethodConstructor) {
      final PsiMethod[] constructors = myParentClass.getConstructors();
      allowFinal = constructors.length <= 1;
    }
    return super.allowFinal() && allowFinal;
  }

  @Override
  protected void updateInitializerSelection() {
    myRbInCurrentMethod.setEnabled(myAllowInitInMethodIfAll || !isReplaceAllOccurrences());
    if (!myRbInCurrentMethod.isEnabled() && myRbInCurrentMethod.isSelected()) {
      myRbInCurrentMethod.setSelected(false);
      myRbInFieldDeclaration.setSelected(true);
    }
  }

  protected JPanel composeWholePanel(JComponent initializerPlacePanel, JPanel checkboxPanel) {
    JPanel panel = new JPanel(new GridBagLayout());
    final GridBagConstraints constraints =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                             new Insets(0, 0, 0, 0), 0, 0);
    panel.add(initializerPlacePanel, constraints);
    panel.add(checkboxPanel, constraints);
    return panel;
  }
}
