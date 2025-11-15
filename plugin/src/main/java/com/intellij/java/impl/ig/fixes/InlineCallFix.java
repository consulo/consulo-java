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
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

public class InlineCallFix extends InspectionGadgetsFix {
    @Nonnull
    @Override
    public LocalizeValue getName() {
        return InspectionGadgetsLocalize.inlineCallQuickfix();
    }

    @Override
    @RequiredWriteAction
    public void doFix(Project project, ProblemDescriptor descriptor) {
        PsiElement nameElement = descriptor.getPsiElement();
        PsiReferenceExpression methodExpression = (PsiReferenceExpression) nameElement.getParent();
        assert methodExpression != null;
        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) methodExpression.getParent();
        JavaRefactoringActionHandlerFactory factory = JavaRefactoringActionHandlerFactory.getInstance();
        RefactoringActionHandler inlineHandler = factory.createInlineHandler();
        Runnable runnable = () -> inlineHandler.invoke(project, new PsiElement[]{methodCallExpression}, null);
        Application application = project.getApplication();
        if (application.isUnitTestMode()) {
            runnable.run();
        }
        else {
            application.invokeLater(runnable, project.getDisposed());
        }
    }
}
