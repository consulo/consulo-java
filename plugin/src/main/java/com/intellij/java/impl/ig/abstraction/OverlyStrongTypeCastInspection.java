/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.abstraction;

import com.intellij.java.impl.ig.psiutils.InstanceOfUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class OverlyStrongTypeCastInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public boolean ignoreInMatchingInstanceof = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.overlyStrongTypeCastDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        PsiType expectedType = (PsiType) infos[0];
        String typeText = expectedType.getPresentableText();
        return InspectionGadgetsLocalize.overlyStrongTypeCastProblemDescriptor(typeText).get();
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.overlyStrongTypeCastIgnoreInMatchingInstanceofOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreInMatchingInstanceof");
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new OverlyStrongCastFix();
    }

    private static class OverlyStrongCastFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.overlyStrongTypeCastWeakenQuickfix();
        }

        @Override
        @RequiredWriteAction
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement castTypeElement = descriptor.getPsiElement();
            PsiTypeCastExpression expression = (PsiTypeCastExpression) castTypeElement.getParent();
            if (expression == null) {
                return;
            }
            PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
            if (expectedType == null) {
                return;
            }
            PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            String newExpression = '(' + expectedType.getCanonicalText() + ')' + operand.getText();
            replaceExpressionAndShorten(expression, newExpression);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new OverlyStrongTypeCastVisitor();
    }

    private class OverlyStrongTypeCastVisitor extends BaseInspectionVisitor {
        @Override
        public void visitTypeCastExpression(@Nonnull PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            PsiType operandType = operand.getType();
            if (operandType == null) {
                return;
            }
            PsiType type = expression.getType();
            if (type == null) {
                return;
            }
            PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, true);
            if (expectedType == null) {
                return;
            }
            if (expectedType.equals(type)) {
                return;
            }
            PsiClass resolved = PsiUtil.resolveClassInType(expectedType);
            if (resolved != null && !resolved.isPhysical()) {
                return;
            }
            if (expectedType.isAssignableFrom(operandType)) {
                //then it's redundant, and caught by the built-in exception
                return;
            }
            if (isTypeParameter(expectedType)) {
                return;
            }
            if (expectedType instanceof PsiArrayType arrayType) {
                PsiType componentType = arrayType.getDeepComponentType();
                if (isTypeParameter(componentType)) {
                    return;
                }
            }
            if (type instanceof PsiPrimitiveType || expectedType instanceof PsiPrimitiveType) {
                return;
            }
            if (PsiPrimitiveType.getUnboxedType(type) != null ||
                PsiPrimitiveType.getUnboxedType(expectedType) != null) {
                return;
            }
            if (expectedType instanceof PsiClassType expectedClassType) {
                PsiClassType expectedRawType = expectedClassType.rawType();
                if (type.equals(expectedRawType)) {
                    return;
                }
                if (type instanceof PsiClassType classType && classType.rawType().equals(expectedRawType)) {
                    return;
                }
                if (type instanceof PsiArrayType) {
                    return;
                }
            }
            if (ignoreInMatchingInstanceof && InstanceOfUtils.hasAgreeingInstanceof(expression)) {
                return;
            }
            PsiTypeElement castTypeElement = expression.getCastType();
            if (castTypeElement == null) {
                return;
            }
            registerError(castTypeElement, expectedType);
        }

        private boolean isTypeParameter(PsiType type) {
            return type instanceof PsiClassType classType && classType.resolve() instanceof PsiTypeParameter;
        }
    }
}