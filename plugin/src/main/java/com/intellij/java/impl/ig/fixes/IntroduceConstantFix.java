/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.fixes;

import com.intellij.java.analysis.refactoring.JavaRefactoringActionHandlerFactory;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class IntroduceConstantFix extends InspectionGadgetsFix {

  @Nonnull
  public LocalizeValue getName() {
    return InspectionGadgetsLocalize.introduceConstantQuickfix();
  }

  public void doFix(@Nonnull final Project project, ProblemDescriptor descriptor) {
    final PsiElement constant = descriptor.getPsiElement();
    Application application = ApplicationManager.getApplication();
    application.invokeLater(new Runnable() {

      public void run() {
        if (!constant.isValid()) return;
        JavaRefactoringActionHandlerFactory factory = JavaRefactoringActionHandlerFactory.getInstance();
        RefactoringActionHandler introduceHandler = factory.createIntroduceConstantHandler();
        DataManager dataManager = DataManager.getInstance();
        DataContext dataContext = dataManager.getDataContext();
        introduceHandler.invoke(project, new PsiElement[]{constant}, dataContext);
      }
    }, project.getDisposed());
  }
}