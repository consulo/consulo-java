/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class StringToStringInspection extends BaseInspection {
    @Override
    @Nonnull
    public String getID() {
        return "RedundantStringToString";
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.stringToStringDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.stringToStringProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new StringToStringVisitor();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new StringToStringFix();
    }

    private static class StringToStringFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.constantConditionalExpressionSimplifyQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiMethodCallExpression call = (PsiMethodCallExpression) descriptor.getPsiElement();
            PsiReferenceExpression expression = call.getMethodExpression();
            PsiExpression qualifier = expression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            String qualifierText = qualifier.getText();
            replaceExpression(call, qualifierText);
        }
    }

    private static class StringToStringVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.TO_STRING.equals(methodName)) {
                return;
            }

            PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != 0) {
                return;
            }
            PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            String className = aClass.getQualifiedName();
            if (!CommonClassNames.JAVA_LANG_STRING.equals(className)) {
                return;
            }
            registerError(expression);
        }
    }
}
