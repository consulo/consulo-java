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
package com.intellij.java.impl.ig.maturity;

import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ThrowablePrintStackTraceInspection extends BaseInspection {
    @Nonnull
    public String getID() {
        return "CallToPrintStackTrace";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.printstacktraceCallDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.printstacktraceCallProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ThrowablePrintStackTraceVisitor();
    }

    private static class ThrowablePrintStackTraceVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final String methodName = MethodCallUtils.getMethodName(expression);
            if (!HardcodedMethodConstants.PRINT_STACK_TRACE.equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList.getExpressions().length != 0) {
                return;
            }
            registerMethodCallError(expression);
        }
    }
}