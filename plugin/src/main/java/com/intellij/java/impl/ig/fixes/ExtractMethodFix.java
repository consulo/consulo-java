/*
 * Copyright 2003-2005 Dave Griffith
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
import com.intellij.java.language.psi.PsiExpression;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.application.ApplicationManager;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class ExtractMethodFix extends InspectionGadgetsFix {
  @Nonnull
  public String getName() {
    return InspectionGadgetsLocalize.extractMethodQuickfix().get();
  }

  public void doFix(final Project project, ProblemDescriptor descriptor) {
    final PsiExpression expression = (PsiExpression)descriptor.getPsiElement();
    final JavaRefactoringActionHandlerFactory factory = JavaRefactoringActionHandlerFactory.getInstance();
    final RefactoringActionHandler extractHandler = factory.createExtractMethodHandler();
    final DataManager dataManager = DataManager.getInstance();
    final DataContext dataContext = dataManager.getDataContext();
    final Runnable runnable = () -> extractHandler.invoke(project, new PsiElement[]{expression}, dataContext);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
  }
}