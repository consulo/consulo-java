/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.naming;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class OverloadedMethodsWithSameNumberOfParametersInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public boolean ignoreInconvertibleTypes = true;

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.overloadedMethodsWithSameNumberParametersDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.overloadedMethodsWithSameNumberParametersProblemDescriptor().get();
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.overloadedMethodsWithSameNumberParametersOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreInconvertibleTypes");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new OverloadedMethodsWithSameNumberOfParametersVisitor();
    }

    private class OverloadedMethodsWithSameNumberOfParametersVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            if (method.isConstructor()) {
                return;
            }
            if (method.getNameIdentifier() == null) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            int parameterCount = parameterList.getParametersCount();
            if (parameterCount == 0) {
                return;
            }
            PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            if (MethodUtils.hasSuper(method)) {
                return;
            }
            String methodName = method.getName();
            PsiMethod[] sameNameMethods = aClass.findMethodsByName(methodName, false);
            for (PsiMethod sameNameMethod : sameNameMethods) {
                if (method.equals(sameNameMethod)) {
                    continue;
                }
                PsiParameterList otherParameterList = sameNameMethod.getParameterList();
                if (parameterCount == otherParameterList.getParametersCount()) {
                    if (ignoreInconvertibleTypes && !areParameterTypesConvertible(parameterList, otherParameterList)) {
                        return;
                    }
                    registerMethodError(method);
                    return;
                }
            }
        }

        private boolean areParameterTypesConvertible(PsiParameterList parameterList, PsiParameterList otherParameterList) {
            PsiParameter[] parameters = parameterList.getParameters();
            PsiParameter[] otherParameters = otherParameterList.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                PsiType type = parameters[i].getType();
                PsiType otherType = otherParameters[i].getType();
                if (!type.isAssignableFrom(otherType) && !otherType.isAssignableFrom(type)) {
                    return false;
                }
            }
            return true;
        }
    }
}