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
package com.intellij.java.impl.ig.security;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class SystemSetSecurityManagerInspection extends BaseInspection {
    @Nonnull
    public String getID() {
        return "CallToSystemSetSecurityManager";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.systemSetSecurityManagerDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.systemSetSecurityManagerProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SystemSetSecurityManagerVisitor();
    }

    private static class SystemSetSecurityManagerVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!isSetSecurityManager(expression)) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isSetSecurityManager(PsiMethodCallExpression expression) {
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!"setSecurityManager".equals(methodName)) {
                return false;
            }
            PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return false;
            }
            PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return false;
            }
            String className = aClass.getQualifiedName();
            if (className == null) {
                return false;
            }
            return "java.lang.System".equals(className);
        }
    }
}
