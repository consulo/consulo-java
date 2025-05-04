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

import com.intellij.java.analysis.impl.find.findUsages.JavaFindUsagesOptions;
import com.intellij.java.analysis.impl.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import consulo.find.FindSettings;
import consulo.find.FindUsagesHandler;
import consulo.find.FindUsagesOptions;
import consulo.find.localize.FindLocalize;
import consulo.find.ui.CommonFindUsagesDialog;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.ex.StateRestoringCheckBoxWrapper;
import jakarta.annotation.Nonnull;

import javax.swing.*;

public abstract class JavaFindUsagesDialog<T extends JavaFindUsagesOptions> extends CommonFindUsagesDialog {
  private StateRestoringCheckBoxWrapper myCbIncludeOverloadedMethods;
  private boolean myIncludeOverloadedMethodsAvailable;

  protected JavaFindUsagesDialog(@Nonnull PsiElement element,
                                 @Nonnull Project project,
                                 @Nonnull FindUsagesOptions findUsagesOptions,
                                 boolean toShowInNewTab,
                                 boolean mustOpenInNewTab,
                                 boolean isSingleFile,
                                 FindUsagesHandler handler) {
    super(element, project, findUsagesOptions, toShowInNewTab, mustOpenInNewTab, isSingleFile, handler);
  }

  @Override
  protected void init() {
    myIncludeOverloadedMethodsAvailable = myPsiElement instanceof PsiMethod && MethodSignatureUtil.hasOverloads((PsiMethod)myPsiElement);
    super.init();
  }

  public void calcFindUsagesOptions(T options) {
    if (options instanceof JavaMethodFindUsagesOptions) {
      ((JavaMethodFindUsagesOptions)options).isIncludeOverloadUsages =
        myIncludeOverloadedMethodsAvailable && isToChange(myCbIncludeOverloadedMethods) && myCbIncludeOverloadedMethods.getValue();
    }
  }

  @Override
  public void calcFindUsagesOptions(FindUsagesOptions options) {
    super.calcFindUsagesOptions(options);
    calcFindUsagesOptions((T)options);
  }

  @Override
  protected void doOKAction() {
    if (shouldDoOkAction()) {
      if (myIncludeOverloadedMethodsAvailable) {
        FindSettings.getInstance().setSearchOverloadedMethods(myCbIncludeOverloadedMethods.getValue());
      }
    }
    else {
      return;
    }
    super.doOKAction();
  }

  @Override
  protected void addUsagesOptions(JPanel optionsPanel) {
    super.addUsagesOptions(optionsPanel);
    if (myIncludeOverloadedMethodsAvailable) {
      myCbIncludeOverloadedMethods = addCheckboxToPanel(FindLocalize.findOptionsIncludeOverloadedMethodsCheckbox(),
                                                        FindSettings.getInstance().isSearchOverloadedMethods(), optionsPanel, false);

    }
  }

  protected final PsiElement getPsiElement() {
    return myPsiElement;
  }

  protected T getFindUsagesOptions() {
    return (T)myFindUsagesOptions;
  }
}
