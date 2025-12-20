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
package com.intellij.java.impl.ig.portability;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class SystemGetenvInspection extends BaseInspection {
    @Nonnull
    public String getID() {
        return "CallToSystemGetenv";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.systemGetenvCallDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.systemGetenvCallProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SystemGetenvVisitor();
    }

    private static class SystemGetenvVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            String getenv = "getenv";
            if (!getenv.equals(methodName)) {
                return;
            }
            PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != 1) {
                return;
            }
            PsiParameter[] parameters = parameterList.getParameters();
            PsiType parameterType = parameters[0].getType();
            if (!TypeUtils.isJavaLangString(parameterType)) {
                return;
            }
            PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            String className = aClass.getQualifiedName();
            if (!"java.lang.System".equals(className)) {
                return;
            }
            registerMethodCallError(expression);
        }
    }
}