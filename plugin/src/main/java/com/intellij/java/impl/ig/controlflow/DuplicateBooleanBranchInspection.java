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
package com.intellij.java.impl.ig.controlflow;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.java.analysis.impl.codeInspection.EquivalenceChecker;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class DuplicateBooleanBranchInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.duplicateBooleanBranchDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.duplicateBooleanBranchProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DuplicateBooleanBranchVisitor();
    }

    private static class DuplicateBooleanBranchVisitor extends BaseInspectionVisitor {

        @Override
        public void visitPolyadicExpression(PsiPolyadicExpression expression) {
            super.visitPolyadicExpression(expression);
            IElementType tokenType = expression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.ANDAND) && !tokenType.equals(JavaTokenType.OROR)) {
                return;
            }
            PsiElement parent = expression.getParent();
            while (parent instanceof PsiParenthesizedExpression) {
                parent = parent.getParent();
            }
            if (parent instanceof PsiBinaryExpression) {
                PsiBinaryExpression parentExpression = (PsiBinaryExpression) parent;
                if (tokenType.equals(parentExpression.getOperationTokenType())) {
                    return;
                }
            }
            Set<PsiExpression> conditions = new HashSet<PsiExpression>();
            collectConditions(expression, conditions, tokenType);
            int numConditions = conditions.size();
            if (numConditions < 2) {
                return;
            }
            PsiExpression[] conditionArray = conditions.toArray(new PsiExpression[numConditions]);
            boolean[] matched = new boolean[conditionArray.length];
            Arrays.fill(matched, false);
            for (int i = 0; i < conditionArray.length; i++) {
                if (matched[i]) {
                    continue;
                }
                PsiExpression condition = conditionArray[i];
                for (int j = i + 1; j < conditionArray.length; j++) {
                    if (matched[j]) {
                        continue;
                    }
                    PsiExpression testCondition = conditionArray[j];
                    boolean areEquivalent =
                        EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(condition, testCondition);
                    if (areEquivalent) {
                        registerError(testCondition);
                        if (!matched[i]) {
                            registerError(condition);
                        }
                        matched[i] = true;
                        matched[j] = true;
                    }
                }
            }
        }

        private static void collectConditions(PsiExpression condition, Set<PsiExpression> conditions, IElementType tokenType) {
            if (condition == null) {
                return;
            }
            if (condition instanceof PsiParenthesizedExpression) {
                PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) condition;
                PsiExpression contents = parenthesizedExpression.getExpression();
                collectConditions(contents, conditions, tokenType);
                return;
            }
            if (condition instanceof PsiPolyadicExpression) {
                PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) condition;
                IElementType testTokeType = polyadicExpression.getOperationTokenType();
                if (testTokeType.equals(tokenType)) {
                    PsiExpression[] operands = polyadicExpression.getOperands();
                    for (PsiExpression operand : operands) {
                        collectConditions(operand, conditions, tokenType);
                    }
                    return;
                }
            }
            conditions.add(condition);
        }
    }
}