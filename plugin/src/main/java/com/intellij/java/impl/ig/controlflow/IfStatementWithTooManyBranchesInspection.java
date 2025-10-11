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

import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.deadCodeNotWorking.impl.SingleIntegerFieldOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

public abstract class IfStatementWithTooManyBranchesInspection extends BaseInspection {
    private static final int DEFAULT_BRANCH_LIMIT = 3;

    /**
     * this is public for the DefaultJDOMExternalizer thingy
     *
     * @noinspection PublicField
     */
    public int m_limit = DEFAULT_BRANCH_LIMIT;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.ifStatementWithTooManyBranchesDisplayName();
    }

    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.ifStatementWithTooManyBranchesMaxOption();
        return new SingleIntegerFieldOptionsPanel(message.get(), this, "m_limit");
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        final Integer branchCount = (Integer) infos[0];
        return InspectionGadgetsLocalize.ifStatementWithTooManyBranchesProblemDescriptor(branchCount).get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new IfStatementWithTooManyBranchesVisitor();
    }

    private class IfStatementWithTooManyBranchesVisitor extends BaseInspectionVisitor {
        @Override
        public void visitIfStatement(@Nonnull PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiElement parent = statement.getParent();
            if (parent instanceof PsiIfStatement) {
                final PsiIfStatement parentStatement = (PsiIfStatement) parent;
                final PsiStatement elseBranch = parentStatement.getElseBranch();
                if (statement.equals(elseBranch)) {
                    return;
                }
            }
            final int branchCount = calculateBranchCount(statement);
            if (branchCount <= m_limit) {
                return;
            }
            registerStatementError(statement, branchCount);
        }

        private int calculateBranchCount(PsiIfStatement statement) {
            final PsiStatement branch = statement.getElseBranch();
            if (branch == null) {
                return 1;
            }
            if (!(branch instanceof PsiIfStatement)) {
                return 2;
            }
            return 1 + calculateBranchCount((PsiIfStatement) branch);
        }
    }
}