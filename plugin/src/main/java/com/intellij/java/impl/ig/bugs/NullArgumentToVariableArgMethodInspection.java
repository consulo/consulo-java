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
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class NullArgumentToVariableArgMethodInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.nullArgumentToVarArgMethodDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.nullArgumentToVarArgMethodProblemDescriptor().get();
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NullArgumentToVariableArgVisitor();
    }

    private static class NullArgumentToVariableArgVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(
            @Nonnull PsiMethodCallExpression call
        ) {
            super.visitMethodCallExpression(call);

            if (!PsiUtil.isLanguageLevel5OrHigher(call)) {
                return;
            }
            PsiExpressionList argumentList = call.getArgumentList();
            PsiExpression[] args = argumentList.getExpressions();
            if (args.length == 0) {
                return;
            }
            PsiExpression lastArg = args[args.length - 1];
            if (!ExpressionUtils.isNullLiteral(lastArg)) {
                return;
            }
            PsiMethod method = call.resolveMethod();
            if (method == null) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != args.length) {
                return;
            }
            PsiParameter[] parameters = parameterList.getParameters();
            PsiParameter lastParameter =
                parameters[parameters.length - 1];
            if (!lastParameter.isVarArgs()) {
                return;
            }
            registerError(lastArg);
        }
    }
}