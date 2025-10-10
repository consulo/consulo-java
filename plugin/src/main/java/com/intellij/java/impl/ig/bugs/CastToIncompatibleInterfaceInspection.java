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

import com.intellij.java.impl.ig.psiutils.InheritanceUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class CastToIncompatibleInterfaceInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.castingToIncompatibleInterfaceDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.castingToIncompatibleInterfaceProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CastToIncompatibleInterfaceVisitor();
    }

    private static class CastToIncompatibleInterfaceVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitTypeCastExpression(
            @Nonnull PsiTypeCastExpression expression
        ) {
            super.visitTypeCastExpression(expression);
            final PsiTypeElement castTypeElement = expression.getCastType();
            if (castTypeElement == null) {
                return;
            }
            final PsiType castType = castTypeElement.getType();
            if (!(castType instanceof PsiClassType)) {
                return;
            }
            final PsiClassType castClassType = (PsiClassType) castType;
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            final PsiType operandType = operand.getType();
            if (!(operandType instanceof PsiClassType)) {
                return;
            }
            final PsiClassType operandClassType = (PsiClassType) operandType;
            final PsiClass castClass = castClassType.resolve();
            if (castClass == null) {
                return;
            }
            if (!castClass.isInterface()) {
                return;
            }
            final PsiClass operandClass = operandClassType.resolve();
            if (operandClass == null) {
                return;
            }
            if (operandClass.isInterface()) {
                return;
            }
            if (InheritanceUtil.existsMutualSubclass(operandClass, castClass)) {
                return;
            }
            registerError(castTypeElement);
        }
    }
}