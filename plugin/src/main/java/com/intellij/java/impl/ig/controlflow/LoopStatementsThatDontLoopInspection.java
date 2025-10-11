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
package com.intellij.java.impl.ig.controlflow;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class LoopStatementsThatDontLoopInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "LoopStatementThatDoesntLoop";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.loopStatementsThatDontLoopDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.loopStatementsThatDontLoopProblemDescriptor().get();
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LoopStatementsThatDontLoopVisitor();
    }

    private static class LoopStatementsThatDontLoopVisitor extends BaseInspectionVisitor {
        @Override
        public void visitForStatement(@Nonnull PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (ControlFlowUtils.statementMayCompleteNormally(body)) {
                return;
            }
            if (ControlFlowUtils.statementIsContinueTarget(statement)) {
                return;
            }
            registerStatementError(statement);
        }

        @Override
        public void visitForeachStatement(
            @Nonnull PsiForeachStatement statement
        ) {
            super.visitForeachStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (ControlFlowUtils.statementMayCompleteNormally(body)) {
                return;
            }
            if (ControlFlowUtils.statementIsContinueTarget(statement)) {
                return;
            }
            registerStatementError(statement);
        }

        @Override
        public void visitWhileStatement(@Nonnull PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (ControlFlowUtils.statementMayCompleteNormally(body)) {
                return;
            }
            if (ControlFlowUtils.statementIsContinueTarget(statement)) {
                return;
            }
            registerStatementError(statement);
        }

        @Override
        public void visitDoWhileStatement(
            @Nonnull PsiDoWhileStatement statement
        ) {
            super.visitDoWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (ControlFlowUtils.statementMayCompleteNormally(body)) {
                return;
            }
            if (ControlFlowUtils.statementIsContinueTarget(statement)) {
                return;
            }
            registerStatementError(statement);
        }
    }
}