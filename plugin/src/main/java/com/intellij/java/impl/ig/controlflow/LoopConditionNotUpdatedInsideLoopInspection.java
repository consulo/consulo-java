/*
 * Copyright 2006-2007 Bas Leijdekkers
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

import com.intellij.java.impl.ig.psiutils.IteratorUtils;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.util.collection.SmartList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.List;

@ExtensionImpl
public class LoopConditionNotUpdatedInsideLoopInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public boolean ignoreIterators = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.loopConditionNotUpdatedInsideLoopDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.loopConditionNotUpdatedInsideLoopProblemDescriptor().get();
    }

    @Nullable
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.ignoreIteratorLoopVariables();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreIterators");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LoopConditionNotUpdatedInsideLoopVisitor();
    }

    private class LoopConditionNotUpdatedInsideLoopVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            PsiExpression condition = statement.getCondition();
            check(condition, statement);
        }

        @Override
        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            PsiExpression condition = statement.getCondition();
            check(condition, statement);
        }

        @Override
        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            PsiExpression condition = statement.getCondition();
            check(condition, statement);
        }

        private void check(PsiExpression condition, PsiStatement statement) {
            List<PsiExpression> notUpdated =
                new SmartList<PsiExpression>();
            if (checkCondition(condition, statement, notUpdated)) {
                if (notUpdated.isEmpty()) {
                    // condition involves only final variables and/or constants,
                    // flag the whole condition
                    // Maybe this should show a different error message, like
                    // "loop condition cannot change",
                    // "loop condition is never updated" or so
                    if (!BoolUtils.isTrue(condition)) {
                        registerError(condition);
                    }
                }
                else {
                    for (PsiExpression expression : notUpdated) {
                        registerError(expression);
                    }
                }
            }
        }

        private boolean checkCondition(
            @Nullable PsiExpression condition,
            @Nonnull PsiStatement context,
            List<PsiExpression> notUpdated
        ) {
            if (condition == null) {
                return false;
            }
            if (PsiUtil.isConstantExpression(condition) ||
                PsiKeyword.NULL.equals(condition.getText())) {
                return true;
            }
            if (condition instanceof PsiInstanceOfExpression) {
                PsiInstanceOfExpression instanceOfExpression =
                    (PsiInstanceOfExpression) condition;
                PsiExpression operand = instanceOfExpression.getOperand();
                return checkCondition(operand, context, notUpdated);
            }
            else if (condition instanceof PsiParenthesizedExpression) {
                // catch stuff like "while ((x)) { ... }"
                PsiExpression expression =
                    ((PsiParenthesizedExpression) condition).getExpression();
                return checkCondition(expression, context, notUpdated);
            }
            else if (condition instanceof PsiBinaryExpression) {
                // while (value != x) { ... }
                // while (value != (x + y)) { ... }
                // while (b1 && b2) { ... }
                PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) condition;
                PsiExpression lhs = binaryExpression.getLOperand();
                PsiExpression rhs = binaryExpression.getROperand();
                if (rhs == null) {
                    return false;
                }
                if (checkCondition(lhs, context, notUpdated)) {
                    return checkCondition(rhs, context, notUpdated);
                }
            }
            else if (condition instanceof PsiReferenceExpression) {
                PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) condition;
                PsiElement element = referenceExpression.resolve();
                if (element instanceof PsiField) {
                    PsiField field = (PsiField) element;
                    PsiType type = field.getType();
                    if (field.hasModifierProperty(PsiModifier.FINAL) &&
                        type.getArrayDimensions() == 0) {
                        if (field.hasModifierProperty(PsiModifier.STATIC)) {
                            return true;
                        }
                        PsiExpression qualifier =
                            referenceExpression.getQualifierExpression();
                        if (qualifier == null) {
                            return true;
                        }
                        else if (checkCondition(qualifier, context,
                            notUpdated
                        )) {
                            return true;
                        }
                    }
                }
                else if (element instanceof PsiVariable) {
                    PsiVariable variable = (PsiVariable) element;
                    if (variable.hasModifierProperty(PsiModifier.FINAL)) {
                        // final variables cannot be updated, don't bother to
                        // flag them
                        return true;
                    }
                    else if (element instanceof PsiLocalVariable ||
                        element instanceof PsiParameter) {
                        if (!VariableAccessUtils.variableIsAssigned(
                            variable,
                            context
                        )) {
                            notUpdated.add(referenceExpression);
                            return true;
                        }
                    }
                }
            }
            else if (condition instanceof PsiPrefixExpression) {
                PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression) condition;
                IElementType tokenType = prefixExpression.getOperationTokenType();
                if (JavaTokenType.EXCL.equals(tokenType) ||
                    JavaTokenType.PLUS.equals(tokenType) ||
                    JavaTokenType.MINUS.equals(tokenType)) {
                    PsiExpression operand = prefixExpression.getOperand();
                    return checkCondition(operand, context, notUpdated);
                }
            }
            else if (condition instanceof PsiArrayAccessExpression) {
                // Actually the contents of the array could change nevertheless
                // if it is accessed through a different reference like this:
                //   int[] local_ints = new int[]{1, 2};
                //   int[] other_ints = local_ints;
                //   while (local_ints[0] > 0) { other_ints[0]--; }
                //
                // Keep this check?
                PsiArrayAccessExpression accessExpression =
                    (PsiArrayAccessExpression) condition;
                PsiExpression indexExpression =
                    accessExpression.getIndexExpression();
                return checkCondition(indexExpression, context, notUpdated)
                    && checkCondition(accessExpression.getArrayExpression(),
                    context, notUpdated
                );
            }
            else if (condition instanceof PsiConditionalExpression) {
                PsiConditionalExpression conditionalExpression =
                    (PsiConditionalExpression) condition;
                PsiExpression thenExpression =
                    conditionalExpression.getThenExpression();
                PsiExpression elseExpression =
                    conditionalExpression.getElseExpression();
                if (thenExpression == null || elseExpression == null) {
                    return false;
                }
                return checkCondition(conditionalExpression.getCondition(),
                    context, notUpdated
                )
                    && checkCondition(thenExpression, context, notUpdated)
                    && checkCondition(elseExpression, context, notUpdated);
            }
            else if (condition instanceof PsiThisExpression) {
                return true;
            }
            else if (condition instanceof PsiMethodCallExpression &&
                !ignoreIterators) {
                PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) condition;
                if (!IteratorUtils.isCallToHasNext(methodCallExpression)) {
                    return false;
                }
                PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
                PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
                if (qualifierExpression instanceof PsiReferenceExpression) {
                    PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) qualifierExpression;
                    PsiElement element = referenceExpression.resolve();
                    if (!(element instanceof PsiVariable)) {
                        return false;
                    }
                    PsiVariable variable = (PsiVariable) element;
                    if (!IteratorUtils.containsCallToScannerNext(context,
                        variable, true
                    )) {
                        notUpdated.add(qualifierExpression);
                        return true;
                    }
                }
                else {
                    if (!IteratorUtils.containsCallToScannerNext(context,
                        null, true
                    )) {
                        notUpdated.add(methodCallExpression);
                        return true;
                    }
                }
            }
            return false;
        }
    }
}