/*
 * Copyright 2005-2009 Bas Leijdekkers
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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class SuspiciousSystemArraycopyInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.suspiciousSystemArraycopyDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return (String) infos[0];
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SuspiciousSystemArraycopyVisitor();
    }

    private static class SuspiciousSystemArraycopyVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(
            @Nonnull PsiMethodCallExpression expression
        ) {
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
            @NonNls String name = methodExpression.getReferenceName();
            if (!"arraycopy".equals(name)) {
                return;
            }
            PsiExpression qualifierExpression =
                methodExpression.getQualifierExpression();
            if (!(qualifierExpression instanceof PsiReferenceExpression)) {
                return;
            }
            PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) qualifierExpression;
            String canonicalText = referenceExpression.getCanonicalText();
            if (!canonicalText.equals("java.lang.System")) {
                return;
            }
            PsiExpressionList argumentList = expression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 5) {
                return;
            }
            PsiExpression src = arguments[0];
            PsiType srcType = src.getType();
            PsiExpression srcPos = arguments[1];
            if (isNegativeArgument(srcPos)) {
                LocalizeValue errorString = InspectionGadgetsLocalize.suspiciousSystemArraycopyProblemDescriptor1();
                registerError(srcPos, errorString.get());
            }
            PsiExpression destPos = arguments[3];
            if (isNegativeArgument(destPos)) {
                LocalizeValue errorString = InspectionGadgetsLocalize.suspiciousSystemArraycopyProblemDescriptor2();
                registerError(destPos, errorString.get());
            }
            PsiExpression length = arguments[4];
            if (isNegativeArgument(length)) {
                LocalizeValue errorString = InspectionGadgetsLocalize.suspiciousSystemArraycopyProblemDescriptor3();
                registerError(length, errorString.get());
            }
            boolean notArrayReported = false;
            if (!(srcType instanceof PsiArrayType)) {
                LocalizeValue errorString = InspectionGadgetsLocalize.suspiciousSystemArraycopyProblemDescriptor4();
                registerError(src, errorString.get());
                notArrayReported = true;
            }
            PsiExpression dest = arguments[2];
            PsiType destType = dest.getType();
            if (!(destType instanceof PsiArrayType)) {
                LocalizeValue errorString = InspectionGadgetsLocalize.suspiciousSystemArraycopyProblemDescriptor5();
                registerError(dest, errorString.get());
                notArrayReported = true;
            }
            if (notArrayReported) {
                return;
            }
            PsiArrayType srcArrayType = (PsiArrayType) srcType;
            PsiArrayType destArrayType = (PsiArrayType) destType;
            PsiType srcComponentType = srcArrayType.getComponentType();
            PsiType destComponentType = destArrayType.getComponentType();
            if (!(srcComponentType instanceof PsiPrimitiveType)) {
                if (!destComponentType.isAssignableFrom(srcComponentType)) {
                    LocalizeValue errorString = InspectionGadgetsLocalize.suspiciousSystemArraycopyProblemDescriptor6(
                        srcType.getCanonicalText(),
                        destType.getCanonicalText()
                    );
                    registerError(dest, errorString.get());
                }
            }
            else if (!destComponentType.equals(srcComponentType)) {
                LocalizeValue errorString = InspectionGadgetsLocalize.suspiciousSystemArraycopyProblemDescriptor6(
                    srcType.getCanonicalText(),
                    destType.getCanonicalText()
                );
                registerError(dest, errorString.get());
            }
        }

        private static boolean isNegativeArgument(
            @Nonnull PsiExpression argument
        ) {
            Object constant =
                ExpressionUtils.computeConstantExpression(argument);
            if (!(constant instanceof Integer)) {
                return false;
            }
            Integer integer = (Integer) constant;
            return integer.intValue() < 0;
        }
    }
}