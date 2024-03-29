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
import com.intellij.java.analysis.impl.find.findUsages.JavaPackageFindUsagesOptions;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.StateRestoringCheckBox;

import javax.swing.*;

public class FindPackageUsagesDialog extends JavaFindUsagesDialog<JavaPackageFindUsagesOptions> {
  private StateRestoringCheckBox myCbUsages;
  private StateRestoringCheckBox myCbClassesUsages;

  public FindPackageUsagesDialog(PsiElement element,
                                 Project project,
                                 FindUsagesOptions findUsagesOptions,
                                 boolean toShowInNewTab, boolean mustOpenInNewTab,
                                 boolean isSingleFile, FindUsagesHandler handler) {
    super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
  }

  @Override
  public JComponent getPreferredFocusedControl() {
    return myCbUsages;
  }

  @Override
  public void calcFindUsagesOptions(JavaPackageFindUsagesOptions options) {
    super.calcFindUsagesOptions(options);

    options.isUsages = isSelected(myCbUsages);
    if (isToChange(myCbClassesUsages)){
      options.isClassesUsages = isSelected(myCbClassesUsages);
    }
    options.isSkipPackageStatements = false;
    options.isSkipImportStatements = false;
  }

  @Override
  protected JPanel createFindWhatPanel() {
    JPanel findWhatPanel = new JPanel();
    findWhatPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.what.group"), true));
    findWhatPanel.setLayout(new BoxLayout(findWhatPanel, BoxLayout.Y_AXIS));

    myCbUsages = addCheckboxToPanel(FindBundle.message("find.what.usages.checkbox"), getFindUsagesOptions().isUsages, findWhatPanel, true);
    myCbClassesUsages = addCheckboxToPanel(FindBundle.message("find.what.usages.of.classes.and.interfaces"), getFindUsagesOptions().isClassesUsages, findWhatPanel, true);

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

    boolean hasSelected = isSelected(myCbUsages) || isSelected(myCbClassesUsages);
    setOKActionEnabled(hasSelected);
  }
}