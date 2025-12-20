/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.internationalization;

import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class TimeToStringInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "CallToTimeToString";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.timeTostringCallDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.timeTostringCallProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TimeToStringVisitor();
    }

    private static class TimeToStringVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(
            @Nonnull PsiMethodCallExpression expression
        ) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.TO_STRING.equals(methodName)) {
                return;
            }
            if (NonNlsUtils.isNonNlsAnnotatedUse(expression)) {
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
            if (!"java.sql.Time".equals(className)) {
                return;
            }
            registerMethodCallError(expression);
        }
    }
}