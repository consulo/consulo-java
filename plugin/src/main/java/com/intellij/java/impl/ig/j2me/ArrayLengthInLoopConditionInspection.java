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
package com.intellij.java.impl.ig.j2me;

import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;

@ExtensionImpl
public class ArrayLengthInLoopConditionInspection extends BaseInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.arrayLengthInLoopConditionDisplayName();
    }

    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.arrayLengthInLoopConditionProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ArrayLengthInLoopConditionVisitor();
    }

    private static class ArrayLengthInLoopConditionVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            checkForMethodCalls(condition);
        }

        @Override
        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            checkForMethodCalls(condition);
        }

        @Override
        public void visitDoWhileStatement(
            PsiDoWhileStatement statement
        ) {
            super.visitDoWhileStatement(statement);
            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            checkForMethodCalls(condition);
        }

        private void checkForMethodCalls(PsiExpression condition) {
            PsiElementVisitor visitor =
                new JavaRecursiveElementVisitor() {

                    @Override
                    public void visitReferenceExpression(
                        PsiReferenceExpression expression
                    ) {
                        super.visitReferenceExpression(expression);
                        String name = expression.getReferenceName();
                        if (!HardcodedMethodConstants.LENGTH.equals(name)) {
                            return;
                        }
                        PsiExpression qualifier =
                            expression.getQualifierExpression();
                        if (qualifier == null) {
                            return;
                        }
                        PsiType type = qualifier.getType();
                        if (!(type instanceof PsiArrayType)) {
                            return;
                        }
                        PsiElement lengthElement =
                            expression.getReferenceNameElement();
                        if (lengthElement == null) {
                            return;
                        }
                        registerError(lengthElement);
                    }
                };
            condition.accept(visitor);
        }
    }
}