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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class CovariantEqualsInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.covariantEqualsDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.covariantEqualsProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CovariantEqualsVisitor();
    }

    private static class CovariantEqualsVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            // note: no call to super
            String name = method.getName();
            if (!HardcodedMethodConstants.EQUALS.equals(name)) {
                return;
            }
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != 1) {
                return;
            }
            PsiParameter[] parameters = parameterList.getParameters();
            PsiType argType = parameters[0].getType();
            if (TypeUtils.isJavaLangObject(argType)) {
                return;
            }
            PsiClass aClass = method.getContainingClass();
            if (aClass == null || aClass.isInterface()) {
                return;
            }
            PsiMethod[] methods = aClass.getMethods();
            for (PsiMethod method1 : methods) {
                if (isNonVariantEquals(method1)) {
                    return;
                }
            }
            registerMethodError(method);
        }

        private static boolean isNonVariantEquals(PsiMethod method) {
            String name = method.getName();
            if (!HardcodedMethodConstants.EQUALS.equals(name)) {
                return false;
            }
            PsiParameterList paramList = method.getParameterList();
            PsiParameter[] parameters = paramList.getParameters();
            if (parameters.length != 1) {
                return false;
            }
            PsiType argType = parameters[0].getType();
            return TypeUtils.isJavaLangObject(argType);
        }
    }
}