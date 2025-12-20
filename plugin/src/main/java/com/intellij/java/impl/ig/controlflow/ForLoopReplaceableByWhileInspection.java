/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

@ExtensionImpl
public class ForLoopReplaceableByWhileInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreLoopsWithoutConditions = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.forLoopReplaceableByWhileDisplayName();
    }

    @Override
    @Nonnull
    public String getID() {
        return "ForLoopReplaceableByWhile";
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.forLoopReplaceableByWhileProblemDescriptor().get();
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.forLoopReplaceableByWhileIgnoreOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "m_ignoreLoopsWithoutConditions");
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new ReplaceForByWhileFix();
    }

    private static class ReplaceForByWhileFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.forLoopReplaceableByWhileReplaceQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement forKeywordElement = descriptor.getPsiElement();
            PsiForStatement forStatement = (PsiForStatement) forKeywordElement.getParent();
            assert forStatement != null;
            PsiExpression condition = forStatement.getCondition();
            PsiStatement body = forStatement.getBody();
            String bodyText;
            if (body == null) {
                bodyText = "";
            }
            else {
                bodyText = body.getText();
            }
            @NonNls String whileStatement;
            if (condition == null) {
                whileStatement = "while(true)" + bodyText;
            }
            else {
                whileStatement = "while(" + condition.getText() + ')' +
                    bodyText;
            }
            replaceStatement(forStatement, whileStatement);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ForLoopReplaceableByWhileVisitor();
    }

    private class ForLoopReplaceableByWhileVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitForStatement(
            @Nonnull PsiForStatement statement
        ) {
            super.visitForStatement(statement);
            PsiStatement initialization = statement.getInitialization();
            if (initialization != null &&
                !(initialization instanceof PsiEmptyStatement)) {
                return;
            }
            PsiStatement update = statement.getUpdate();
            if (update != null && !(update instanceof PsiEmptyStatement)) {
                return;
            }
            if (m_ignoreLoopsWithoutConditions) {
                PsiExpression condition = statement.getCondition();
                if (condition == null) {
                    return;
                }
                String conditionText = condition.getText();
                if (PsiKeyword.TRUE.equals(conditionText)) {
                    return;
                }
            }
            registerStatementError(statement);
        }
    }
}