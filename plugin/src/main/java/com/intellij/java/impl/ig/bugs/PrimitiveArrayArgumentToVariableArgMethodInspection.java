/*
 * Copyright 2006-2013 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class PrimitiveArrayArgumentToVariableArgMethodInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.primitiveArrayArgumentToVarArgMethodDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.primitiveArrayArgumentToVarArgMethodProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new PrimitiveArrayArgumentToVariableArgVisitor();
    }

    private static class PrimitiveArrayArgumentToVariableArgVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            if (!PsiUtil.isLanguageLevel5OrHigher(call)) {
                return;
            }
            PsiExpressionList argumentList = call.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length == 0) {
                return;
            }
            PsiExpression lastArgument = arguments[arguments.length - 1];
            PsiType argumentType = lastArgument.getType();
            if (!isPrimitiveArrayType(argumentType)) {
                return;
            }
            JavaResolveResult result = call.resolveMethodGenerics();
            PsiMethod method = (PsiMethod) result.getElement();
            if (method == null) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != arguments.length) {
                return;
            }
            PsiParameter[] parameters = parameterList.getParameters();
            PsiParameter lastParameter = parameters[parameters.length - 1];
            if (!lastParameter.isVarArgs()) {
                return;
            }
            PsiType parameterType = lastParameter.getType();
            if (isDeepPrimitiveArrayType(parameterType, result.getSubstitutor())) {
                return;
            }
            registerError(lastArgument);
        }
    }

    private static boolean isPrimitiveArrayType(PsiType type) {
        if (!(type instanceof PsiArrayType)) {
            return false;
        }
        PsiType componentType = ((PsiArrayType) type).getComponentType();
        return TypeConversionUtil.isPrimitiveAndNotNull(componentType);
    }

    private static boolean isDeepPrimitiveArrayType(PsiType type, PsiSubstitutor substitutor) {
        if (!(type instanceof PsiEllipsisType)) {
            return false;
        }
        PsiType componentType = type.getDeepComponentType();
        PsiType substitute = substitutor.substitute(componentType);
        return TypeConversionUtil.isPrimitiveAndNotNull(substitute.getDeepComponentType());
    }
}