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
package com.intellij.java.impl.ig.security;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class SystemPropertiesInspection extends BaseInspection {
    @Override
    @Nonnull
    public String getID() {
        return "AccessOfSystemProperties";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.systemPropertiesDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        boolean isGetSystemProperty = (Boolean) infos[0];
        boolean isIntegerGetInteger = (Boolean) infos[1];
        if (isGetSystemProperty) {
            return InspectionGadgetsLocalize.systemSetProblemDescriptor().get();
        }
        else if (isIntegerGetInteger) {
            return InspectionGadgetsLocalize.systemPropertiesProblemDescriptor().get();
        }
        else {
            return InspectionGadgetsLocalize.systemPropertiesProblemDescriptor1().get();
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SystemPropertiesVisitor();
    }

    private static class SystemPropertiesVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            boolean isGetSystemProperty = isGetSystemProperty(expression);
            boolean isIntegerGetInteger = isIntegerGetInteger(expression);
            boolean isBooleanGetBoolean = isBooleanGetBoolean(expression);
            if (!(isGetSystemProperty || isIntegerGetInteger || isBooleanGetBoolean)) {
                return;
            }
            registerMethodCallError(expression, Boolean.valueOf(isGetSystemProperty), Boolean.valueOf(isIntegerGetInteger));
        }

        private static boolean isGetSystemProperty(PsiMethodCallExpression expression) {
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!"getProperty".equals(methodName)
                && !"getProperties".equals(methodName)
                && !"setProperty".equals(methodName)
                && !"setProperties".equals(methodName)
                && !"clearProperties".equals(methodName)
            ) {
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

        private static boolean isIntegerGetInteger(PsiMethodCallExpression expression) {
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!"getInteger".equals(methodName)) {
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
            return CommonClassNames.JAVA_LANG_INTEGER.equals(className);
        }

        private static boolean isBooleanGetBoolean(PsiMethodCallExpression expression) {
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!"getBoolean".equals(methodName)) {
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
            return CommonClassNames.JAVA_LANG_BOOLEAN.equals(className);
        }
    }
}