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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class RandomDoubleForRandomIntegerInspection extends BaseInspection {
    @Override
    @Nonnull
    public String getID() {
        return "UsingRandomNextDoubleForRandomInteger";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.randomDoubleForRandomIntegerDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.randomDoubleForRandomIntegerProblemDescriptor().get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new RandomDoubleForRandomIntegerFix();
    }

    private static class RandomDoubleForRandomIntegerFix extends InspectionGadgetsFix {
        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.randomDoubleForRandomIntegerReplaceQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiIdentifier name = (PsiIdentifier) descriptor.getPsiElement();
            PsiReferenceExpression expression = (PsiReferenceExpression) name.getParent();
            if (expression == null) {
                return;
            }
            PsiExpression call = (PsiExpression) expression.getParent();
            PsiExpression qualifier = expression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            String qualifierText = qualifier.getText();
            PsiBinaryExpression multiplication = (PsiBinaryExpression) getContainingExpression(call);
            if (multiplication == null) {
                return;
            }
            PsiExpression cast = getContainingExpression(multiplication);
            if (cast == null) {
                return;
            }
            PsiExpression multiplierExpression;
            PsiExpression lhs = multiplication.getLOperand();
            PsiExpression strippedLhs = ParenthesesUtils.stripParentheses(lhs);
            if (call.equals(strippedLhs)) {
                multiplierExpression = multiplication.getROperand();
            }
            else {
                multiplierExpression = lhs;
            }
            assert multiplierExpression != null;
            String multiplierText = multiplierExpression.getText();
            String nextInt = ".nextInt((int) ";
            replaceExpression(cast, qualifierText + nextInt + multiplierText + ')');
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new RandomDoubleForRandomIntegerVisitor();
    }

    private static class RandomDoubleForRandomIntegerVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            PsiReferenceExpression methodExpression = call.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            String nextDouble = "nextDouble";
            if (!nextDouble.equals(methodName)) {
                return;
            }
            PsiMethod method = call.resolveMethod();
            if (method == null) {
                return;
            }
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            String className = containingClass.getQualifiedName();
            if (!"java.util.Random".equals(className)) {
                return;
            }
            PsiExpression possibleMultiplierExpression = getContainingExpression(call);
            if (!isMultiplier(possibleMultiplierExpression)) {
                return;
            }
            PsiExpression possibleIntCastExpression = getContainingExpression(possibleMultiplierExpression);
            if (!isIntCast(possibleIntCastExpression)) {
                return;
            }
            registerMethodCallError(call);
        }

        private static boolean isMultiplier(PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            if (!(expression instanceof PsiBinaryExpression)) {
                return false;
            }
            PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
            IElementType tokenType = binaryExpression.getOperationTokenType();
            return JavaTokenType.ASTERISK.equals(tokenType);
        }

        private static boolean isIntCast(PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            if (!(expression instanceof PsiTypeCastExpression)) {
                return false;
            }
            PsiTypeCastExpression castExpression = (PsiTypeCastExpression) expression;
            PsiType type = castExpression.getType();
            return PsiType.INT.equals(type);
        }
    }

    @Nullable
    static PsiExpression getContainingExpression(PsiExpression expression) {
        PsiElement ancestor = expression.getParent();
        while (true) {
            if (ancestor == null) {
                return null;
            }
            if (!(ancestor instanceof PsiExpression)) {
                return null;
            }
            if (!(ancestor instanceof PsiParenthesizedExpression)) {
                return (PsiExpression) ancestor;
            }
            ancestor = ancestor.getParent();
        }
    }
}