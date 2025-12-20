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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ForLoopThatDoesntUseLoopVariableInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.forLoopNotUseLoopVariableDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        boolean condition = ((Boolean) infos[0]).booleanValue();
        boolean update = ((Boolean) infos[1]).booleanValue();
        if (condition && update) {
            return InspectionGadgetsLocalize.forLoopNotUseLoopVariableProblemDescriptorBothConditionAndUpdate().get();
        }
        if (condition) {
            return InspectionGadgetsLocalize.forLoopNotUseLoopVariableProblemDescriptorCondition().get();
        }
        return InspectionGadgetsLocalize.forLoopNotUseLoopVariableProblemDescriptorUpdate().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ForLoopThatDoesntUseLoopVariableVisitor();
    }

    private static class ForLoopThatDoesntUseLoopVariableVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitForStatement(@Nonnull PsiForStatement statement) {
            super.visitForStatement(statement);
            if (conditionUsesInitializer(statement)) {
                if (!updateUsesInitializer(statement)) {
                    registerStatementError(statement,
                        Boolean.FALSE, Boolean.TRUE
                    );
                }
            }
            else {
                if (updateUsesInitializer(statement)) {
                    registerStatementError(statement,
                        Boolean.TRUE, Boolean.FALSE
                    );
                }
                else {
                    registerStatementError(statement,
                        Boolean.TRUE, Boolean.TRUE
                    );
                }
            }
        }

        private static boolean conditionUsesInitializer(
            PsiForStatement statement
        ) {
            PsiStatement initialization = statement.getInitialization();
            PsiExpression condition = statement.getCondition();

            if (initialization == null) {
                return true;
            }
            if (condition == null) {
                return true;
            }
            if (!(initialization instanceof PsiDeclarationStatement)) {
                return true;
            }
            PsiDeclarationStatement declaration =
                (PsiDeclarationStatement) initialization;
            PsiElement[] declaredElements =
                declaration.getDeclaredElements();
            if (declaredElements.length != 1) {
                return true;
            }
            if (declaredElements[0] == null ||
                !(declaredElements[0] instanceof PsiLocalVariable)) {
                return true;
            }
            PsiLocalVariable localVar =
                (PsiLocalVariable) declaredElements[0];
            return expressionUsesVariable(condition, localVar);
        }

        private static boolean updateUsesInitializer(PsiForStatement statement) {
            PsiStatement initialization = statement.getInitialization();
            PsiStatement update = statement.getUpdate();

            if (initialization == null) {
                return true;
            }
            if (update == null) {
                return true;
            }
            if (!(initialization instanceof PsiDeclarationStatement)) {
                return true;
            }
            PsiDeclarationStatement declaration =
                (PsiDeclarationStatement) initialization;
            PsiElement[] declaredElements =
                declaration.getDeclaredElements();
            if (declaredElements.length != 1) {
                return true;
            }
            if (declaredElements[0] == null ||
                !(declaredElements[0] instanceof PsiLocalVariable)) {
                return true;
            }
            PsiLocalVariable localVar =
                (PsiLocalVariable) declaredElements[0];
            return statementUsesVariable(update, localVar);
        }

        private static boolean statementUsesVariable(
            PsiStatement statement,
            PsiLocalVariable localVar
        ) {
            UseVisitor useVisitor = new UseVisitor(localVar);
            statement.accept(useVisitor);
            return useVisitor.isUsed();
        }

        private static boolean expressionUsesVariable(
            PsiExpression expression,
            PsiLocalVariable localVar
        ) {
            UseVisitor useVisitor = new UseVisitor(localVar);
            expression.accept(useVisitor);
            return useVisitor.isUsed();
        }
    }

    private static class UseVisitor extends JavaRecursiveElementVisitor {

        private final PsiLocalVariable variable;
        private boolean used = false;

        private UseVisitor(PsiLocalVariable var) {
            super();
            variable = var;
        }

        @Override
        public void visitElement(@Nonnull PsiElement element) {
            if (!used) {
                super.visitElement(element);
            }
        }

        @Override
        public void visitReferenceExpression(
            @Nonnull PsiReferenceExpression ref
        ) {
            if (used) {
                return;
            }
            super.visitReferenceExpression(ref);
            PsiElement resolvedElement = ref.resolve();
            if (variable.equals(resolvedElement)) {
                used = true;
            }
        }

        public boolean isUsed() {
            return used;
        }
    }
}