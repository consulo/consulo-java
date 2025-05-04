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
package com.intellij.java.impl.find.findUsages;

import consulo.find.FindBundle;
import consulo.find.FindUsagesHandler;
import consulo.find.FindUsagesOptions;
import com.intellij.java.analysis.impl.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.find.localize.FindLocalize;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.ui.ex.StateRestoringCheckBoxWrapper;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.StateRestoringCheckBox;

import consulo.ui.ex.awtUnsafe.TargetAWT;
import jakarta.annotation.Nullable;
import javax.swing.*;

public class FindMethodUsagesDialog extends JavaFindUsagesDialog<JavaMethodFindUsagesOptions> {
  private StateRestoringCheckBoxWrapper myCbUsages;
  private StateRestoringCheckBoxWrapper myCbImplementingMethods;
  private StateRestoringCheckBoxWrapper myCbOverridingMethods;
  private boolean myHasFindWhatPanel;

  public FindMethodUsagesDialog(PsiElement element, Project project, FindUsagesOptions findUsagesOptions, boolean toShowInNewTab, boolean mustOpenInNewTab,
                                boolean isSingleFile,
                                FindUsagesHandler handler) {
    super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedControl() {
    return myHasFindWhatPanel ? (JComponent) TargetAWT.to(myCbUsages.getComponent()) : null;
  }

  @Override
  public void calcFindUsagesOptions(JavaMethodFindUsagesOptions options) {
    super.calcFindUsagesOptions(options);

    options.isUsages = isSelected(myCbUsages) || !myHasFindWhatPanel;
    if (isToChange(myCbOverridingMethods)) {
      options.isOverridingMethods = isSelected(myCbOverridingMethods);
    }
    if (isToChange(myCbImplementingMethods)) {
      options.isImplementingMethods = isSelected(myCbImplementingMethods);
    }
    options.isCheckDeepInheritance = true;
  }

  @Override
  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();
    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.what.group"), true));
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel(FindLocalize.findWhatUsagesCheckbox(), getFindUsagesOptions().isUsages, findWhatPanel, true);

    PsiMethod method = (PsiMethod) getPsiElement();
    PsiClass aClass = method.getContainingClass();
    if (!method.isConstructor() &&
            !method.hasModifierProperty(PsiModifier.STATIC) &&
            !method.hasModifierProperty(PsiModifier.FINAL) &&
            !method.hasModifierProperty(PsiModifier.PRIVATE) &&
            aClass != null &&
            !(aClass instanceof PsiAnonymousClass) &&
            !aClass.hasModifierProperty(PsiModifier.FINAL)) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        myCbImplementingMethods = addCheckboxToPanel(FindLocalize.findWhatImplementingMethodsCheckbox(), getFindUsagesOptions().isImplementingMethods, findWhatPanel, true);
      } else {
        myCbOverridingMethods = addCheckboxToPanel(FindLocalize.findWhatOverridingMethodsCheckbox(), getFindUsagesOptions().isOverridingMethods, findWhatPanel, true);
      }
    } else {
      myHasFindWhatPanel = false;
      return null; 
    }
    myHasFindWhatPanel = true;
    return findWhatPanel;

    /*if (method.isConstructor() ||
        method.hasModifierProperty(PsiModifier.STATIC) ||
        method.hasModifierProperty(PsiModifier.FINAL) ||
        method.hasModifierProperty(PsiModifier.PRIVATE) ||
        aClass == null ||
        aClass instanceof PsiAnonymousClass ||
        aClass.hasModifierProperty(PsiModifier.FINAL)){
      myHasFindWhatPanel = false;
      return null;
    }
    else{
      myHasFindWhatPanel = true;
      return findWhatPanel;
    }*/
  }

  @Override
  protected void update() {
    if (!myHasFindWhatPanel) {
      setOKActionEnabled(true);
    } else {

      boolean hasSelected = isSelected(myCbUsages) || isSelected(myCbImplementingMethods) || isSelected(myCbOverridingMethods);
      setOKActionEnabled(hasSelected);
    }
  }
}