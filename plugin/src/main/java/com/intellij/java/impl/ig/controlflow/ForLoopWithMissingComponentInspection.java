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
package com.intellij.java.impl.ig.controlflow;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class ForLoopWithMissingComponentInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean ignoreCollectionLoops = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.forLoopWithMissingComponentDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        final boolean hasInitializer = (Boolean) infos[0];
        final boolean hasCondition = (Boolean) infos[1];
        final boolean hasUpdate = (Boolean) infos[2];
        if (hasInitializer) {
            if (hasCondition) {
                return InspectionGadgetsLocalize.forLoopWithMissingComponentProblemDescriptor3().get();
            }
            else if (hasUpdate) {
                return InspectionGadgetsLocalize.forLoopWithMissingComponentProblemDescriptor2().get();
            }
            else {
                return InspectionGadgetsLocalize.forLoopWithMissingComponentProblemDescriptor6().get();
            }
        }
        else if (hasCondition) {
            if (hasUpdate) {
                return InspectionGadgetsLocalize.forLoopWithMissingComponentProblemDescriptor1().get();
            }
            else {
                return InspectionGadgetsLocalize.forLoopWithMissingComponentProblemDescriptor5().get();
            }
        }
        else if (hasUpdate) {
            return InspectionGadgetsLocalize.forLoopWithMissingComponentProblemDescriptor4().get();
        }
        else {
            return InspectionGadgetsLocalize.forLoopWithMissingComponentProblemDescriptor7().get();
        }
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.forLoopWithMissingComponentCollectionLoopOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreCollectionLoops");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ForLoopWithMissingComponentVisitor();
    }

    private class ForLoopWithMissingComponentVisitor extends BaseInspectionVisitor {
        @Override
        public void visitForStatement(@Nonnull PsiForStatement statement) {
            super.visitForStatement(statement);
            final boolean hasCondition = hasCondition(statement);
            final boolean hasInitializer = hasInitializer(statement);
            final boolean hasUpdate = hasUpdate(statement);
            if (hasCondition && hasInitializer && hasUpdate) {
                return;
            }
            if (ignoreCollectionLoops && isCollectionLoopStatement(statement)) {
                return;
            }
            registerStatementError(statement, hasInitializer, hasCondition, hasUpdate);
        }

        private boolean hasCondition(PsiForStatement statement) {
            return statement.getCondition() != null;
        }

        private boolean hasInitializer(PsiForStatement statement) {
            final PsiStatement initialization = statement.getInitialization();
            return initialization != null && !(initialization instanceof PsiEmptyStatement);
        }

        private boolean hasUpdate(PsiForStatement statement) {
            final PsiStatement update = statement.getUpdate();
            return update != null && !(update instanceof PsiEmptyStatement);
        }

        private boolean isCollectionLoopStatement(PsiForStatement forStatement) {
            final PsiStatement initialization = forStatement.getInitialization();
            if (!(initialization instanceof PsiDeclarationStatement)) {
                return false;
            }
            final PsiDeclarationStatement declaration = (PsiDeclarationStatement) initialization;
            final PsiElement[] declaredElements = declaration.getDeclaredElements();
            for (PsiElement declaredElement : declaredElements) {
                if (!(declaredElement instanceof PsiVariable)) {
                    continue;
                }
                final PsiVariable variable = (PsiVariable) declaredElement;
                final PsiType variableType = variable.getType();
                if (!(variableType instanceof PsiClassType)) {
                    continue;
                }
                final PsiClassType classType = (PsiClassType) variableType;
                final PsiClass declaredClass = classType.resolve();
                if (declaredClass == null) {
                    continue;
                }
                if (!InheritanceUtil.isInheritor(declaredClass, CommonClassNames.JAVA_UTIL_ITERATOR)) {
                    continue;
                }
                final PsiExpression initialValue = variable.getInitializer();
                if (initialValue == null) {
                    continue;
                }
                if (!(initialValue instanceof PsiMethodCallExpression)) {
                    continue;
                }
                final PsiMethodCallExpression initialCall = (PsiMethodCallExpression) initialValue;
                final PsiReferenceExpression initialMethodExpression = initialCall.getMethodExpression();
                final String initialCallName = initialMethodExpression.getReferenceName();
                if (!HardcodedMethodConstants.ITERATOR.equals(initialCallName)) {
                    continue;
                }
                final PsiExpression condition = forStatement.getCondition();
                if (isHasNext(condition, variable)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isHasNext(PsiExpression condition, PsiVariable iterator) {
            if (condition instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) condition;
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                return isHasNext(lhs, iterator) || isHasNext(rhs, iterator);
            }
            if (!(condition instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression call = (PsiMethodCallExpression) condition;
            final PsiExpressionList argumentList = call.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 0) {
                return false;
            }
            final PsiReferenceExpression methodExpression = call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.HAS_NEXT.equals(methodName)) {
                return false;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return true;
            }
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression) qualifier;
            final PsiElement target = referenceExpression.resolve();
            return iterator.equals(target);
        }
    }
}