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
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class EqualsBetweenInconvertibleTypesInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.equalsBetweenInconvertibleTypesDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        PsiType comparedType = (PsiType) infos[0];
        PsiType comparisonType = (PsiType) infos[1];
        return InspectionGadgetsLocalize.equalsBetweenInconvertibleTypesProblemDescriptor(
            comparedType.getPresentableText(),
            comparisonType.getPresentableText()
        ).get();
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EqualsBetweenInconvertibleTypesVisitor();
    }

    private static class EqualsBetweenInconvertibleTypesVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(
            @Nonnull PsiMethodCallExpression expression
        ) {
            super.visitMethodCallExpression(expression);
            if (!MethodCallUtils.isEqualsCall(expression)) {
                return;
            }
            PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
            PsiExpressionList argumentList = expression.getArgumentList();
            PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 1) {
                return;
            }
            PsiExpression expression1 = args[0];
            PsiExpression expression2 =
                methodExpression.getQualifierExpression();
            if (expression2 == null) {
                return;
            }
            PsiType comparedType = expression1.getType();
            if (comparedType == null) {
                return;
            }
            PsiType comparisonType = expression2.getType();
            if (comparisonType == null) {
                return;
            }
            PsiType comparedTypeErasure = TypeConversionUtil.erasure(comparedType);
            PsiType comparisonTypeErasure = TypeConversionUtil.erasure(comparisonType);
            if (comparedTypeErasure == null ||
                comparisonTypeErasure == null ||
                TypeConversionUtil.areTypesConvertible(comparedTypeErasure, comparisonTypeErasure)) {
                return;
            }
            registerMethodCallError(expression, comparedType, comparisonType);
        }
    }
}