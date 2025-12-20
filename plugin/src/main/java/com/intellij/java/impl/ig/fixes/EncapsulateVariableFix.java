/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.application.ApplicationManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class EncapsulateVariableFix extends InspectionGadgetsFix {

  private final String fieldName;

  public EncapsulateVariableFix(String fieldName) {
    this.fieldName = fieldName;
  }

  @Override
  @Nonnull
  public LocalizeValue getName() {
    return InspectionGadgetsLocalize.encapsulateVariableQuickfix(fieldName);
  }

  @Override
  public void doFix(final Project project, ProblemDescriptor descriptor) {
    PsiElement nameElement = descriptor.getPsiElement();
    PsiElement parent = nameElement.getParent();
    final PsiField field;
    if (parent instanceof PsiField) {
      field = (PsiField)parent;
    }
    else if (parent instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)parent;
      PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiField)) {
        return;
      }
      field = (PsiField)target;
    }
    else {
      return;
    }
    JavaRefactoringActionHandlerFactory factory =
      JavaRefactoringActionHandlerFactory.getInstance();
    final RefactoringActionHandler renameHandler =
      factory.createEncapsulateFieldsHandler();
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        renameHandler.invoke(project, new PsiElement[]{field}, null);
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
