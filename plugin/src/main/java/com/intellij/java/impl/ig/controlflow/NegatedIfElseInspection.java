/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

@ExtensionImpl
public class NegatedIfElseInspection extends BaseInspection {
    @SuppressWarnings("PublicField")
    public boolean m_ignoreNegatedNullComparison = true;
    @SuppressWarnings("PublicField")
    public boolean m_ignoreNegatedZeroComparison = false;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "IfStatementWithNegatedCondition";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.negatedIfElseDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.negatedIfElseProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new NegatedIfElseVisitor();
    }

    @Override
    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
        panel.addCheckbox(InspectionGadgetsLocalize.negatedIfElseIgnoreNegatedNullOption().get(), "m_ignoreNegatedNullComparison");
        panel.addCheckbox(InspectionGadgetsLocalize.negatedIfElseIgnoreNegatedZeroOption().get(), "m_ignoreNegatedZeroComparison");
        return panel;
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new NegatedIfElseFix();
    }

    private static class NegatedIfElseFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.negatedIfElseInvertQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement ifToken = descriptor.getPsiElement();
            PsiIfStatement ifStatement = (PsiIfStatement) ifToken.getParent();
            assert ifStatement != null;
            PsiStatement elseBranch = ifStatement.getElseBranch();
            if (elseBranch == null) {
                return;
            }
            PsiStatement thenBranch = ifStatement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            PsiExpression condition = ifStatement.getCondition();
            if (condition == null) {
                return;
            }
            String negatedCondition = BoolUtils.getNegatedExpressionText(condition);
            String elseText = elseBranch.getText();
            PsiElement lastChild = elseBranch.getLastChild();
            if (lastChild instanceof PsiComment) {
                PsiComment comment = (PsiComment) lastChild;
                IElementType tokenType = comment.getTokenType();
                if (JavaTokenType.END_OF_LINE_COMMENT.equals(tokenType)) {
                    elseText += '\n';
                }
            }
            @NonNls String newStatement = "if(" + negatedCondition + ')' + elseText + " else " + thenBranch.getText();
            replaceStatement(ifStatement, newStatement);
        }
    }

    private class NegatedIfElseVisitor extends BaseInspectionVisitor {

        @Override
        public void visitIfStatement(@Nonnull PsiIfStatement statement) {
            super.visitIfStatement(statement);
            PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            PsiStatement elseBranch = statement.getElseBranch();
            if (elseBranch == null) {
                return;
            }
            if (elseBranch instanceof PsiIfStatement) {
                return;
            }
            PsiExpression condition = statement.getCondition();
            if (condition == null) {
                return;
            }
            if (!ExpressionUtils.isNegation(condition, m_ignoreNegatedNullComparison, m_ignoreNegatedZeroComparison)) {
                return;
            }
            PsiElement parent = statement.getParent();
            if (parent instanceof PsiIfStatement) {
                return;
            }
            registerStatementError(statement);
        }
    }
}