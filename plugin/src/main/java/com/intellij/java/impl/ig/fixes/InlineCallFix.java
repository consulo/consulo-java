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

import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.application.ApplicationManager;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.analysis.refactoring.JavaRefactoringActionHandlerFactory;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import jakarta.annotation.Nonnull;

public class InlineCallFix extends InspectionGadgetsFix {

  @Nonnull
  public String getName() {
    return InspectionGadgetsBundle.message("inline.call.quickfix");
  }

  public void doFix(final Project project, ProblemDescriptor descriptor) {
    final PsiElement nameElement = descriptor.getPsiElement();
    final PsiReferenceExpression methodExpression =
      (PsiReferenceExpression)nameElement.getParent();
    assert methodExpression != null;
    final PsiMethodCallExpression methodCallExpression =
      (PsiMethodCallExpression)methodExpression.getParent();
    final JavaRefactoringActionHandlerFactory factory =
      JavaRefactoringActionHandlerFactory.getInstance();
    final RefactoringActionHandler inlineHandler = factory.createInlineHandler();
    final Runnable runnable = new Runnable() {
      public void run() {
        inlineHandler.invoke(project, new PsiElement[]{methodCallExpression}, null);
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable, project.getDisposed());
    }
  }
}
