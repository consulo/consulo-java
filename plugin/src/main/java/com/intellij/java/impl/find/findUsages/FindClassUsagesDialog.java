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
import com.intellij.java.analysis.impl.find.findUsages.JavaClassFindUsagesOptions;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import consulo.find.localize.FindLocalize;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.ui.ex.StateRestoringCheckBoxWrapper;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.StateRestoringCheckBox;
import consulo.ui.ex.awtUnsafe.TargetAWT;

import javax.swing.*;

public class FindClassUsagesDialog extends JavaFindUsagesDialog<JavaClassFindUsagesOptions> {
  private StateRestoringCheckBoxWrapper myCbUsages;
  private StateRestoringCheckBoxWrapper myCbMethodsUsages;
  private StateRestoringCheckBoxWrapper myCbFieldsUsages;
  private StateRestoringCheckBoxWrapper myCbImplementingClasses;
  private StateRestoringCheckBoxWrapper myCbDerivedInterfaces;
  private StateRestoringCheckBoxWrapper myCbDerivedClasses;

  public FindClassUsagesDialog(PsiElement element, Project project, FindUsagesOptions findUsagesOptions, boolean toShowInNewTab, boolean mustOpenInNewTab,
                               boolean isSingleFile,
                               FindUsagesHandler handler){
    super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
  }

  @Override
  public JComponent getPreferredFocusedControl() {
    return (JComponent) TargetAWT.to(myCbUsages.getComponent());
  }

  @Override
  public void calcFindUsagesOptions(JavaClassFindUsagesOptions options) {
    super.calcFindUsagesOptions(options);

    if (isToChange(myCbUsages)){
      options.isUsages = isSelected(myCbUsages);
    }
    if (isToChange(myCbMethodsUsages)){
      options.isMethodsUsages = isSelected(myCbMethodsUsages);
    }
    if (isToChange(myCbFieldsUsages)){
      options.isFieldsUsages = isSelected(myCbFieldsUsages);
    }
    if (isToChange(myCbDerivedClasses)){
      options.isDerivedClasses = isSelected(myCbDerivedClasses);
    }
    if (isToChange(myCbImplementingClasses)){
      options.isImplementingClasses = isSelected(myCbImplementingClasses);
    }
    if (isToChange(myCbDerivedInterfaces)){
      options.isDerivedInterfaces = isSelected(myCbDerivedInterfaces);
    }
    options.isSkipImportStatements = false;
    options.isCheckDeepInheritance = true;
    options.isIncludeInherited = false;
  }

  @Override
  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();

    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.what.group"), true));
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel(FindLocalize.findWhatUsagesCheckbox(), getFindUsagesOptions().isUsages, findWhatPanel, true);

    PsiClass psiClass = (PsiClass)getPsiElement();
    myCbMethodsUsages = addCheckboxToPanel(FindLocalize.findWhatMethodsUsagesCheckbox(), getFindUsagesOptions().isMethodsUsages, findWhatPanel, true);

    if (!psiClass.isAnnotationType()) {
      myCbFieldsUsages = addCheckboxToPanel(FindLocalize.findWhatFieldsUsagesCheckbox(), getFindUsagesOptions().isFieldsUsages, findWhatPanel, true);
      if (psiClass.isInterface()){
        myCbImplementingClasses = addCheckboxToPanel(FindLocalize.findWhatImplementingClassesCheckbox(), getFindUsagesOptions().isImplementingClasses, findWhatPanel, true);
        myCbDerivedInterfaces = addCheckboxToPanel(FindLocalize.findWhatDerivedInterfacesCheckbox(), getFindUsagesOptions().isDerivedInterfaces, findWhatPanel, true);
      }
      else if (!psiClass.hasModifierProperty(PsiModifier.FINAL)){
        myCbDerivedClasses = addCheckboxToPanel(FindLocalize.findWhatDerivedClassesCheckbox(), getFindUsagesOptions().isDerivedClasses, findWhatPanel, true);
      }
    }
    return findWhatPanel;
  }

  @Override
  protected void update() {
    if(myCbToSearchForTextOccurrences != null){
      if (isSelected(myCbUsages)){
        myCbToSearchForTextOccurrences.makeSelectable();
      }
      else{
        myCbToSearchForTextOccurrences.makeUnselectable(false);
      }
    }

    boolean hasSelected = isSelected(myCbUsages) ||
      isSelected(myCbFieldsUsages) ||
      isSelected(myCbMethodsUsages) ||
      isSelected(myCbImplementingClasses) ||
      isSelected(myCbDerivedInterfaces) ||
      isSelected(myCbDerivedClasses);
    setOKActionEnabled(hasSelected);
  }

}