/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;

@ExtensionImpl
public class EmptyStatementBodyInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean m_reportEmptyBlocks = true;

    @Nonnull
    @Override
    @Pattern("[a-zA-Z_0-9.]+")
    public String getID() {
        return "StatementWithEmptyBody";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.statementWithEmptyBodyDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.statementWithEmptyBodyProblemDescriptor().get();
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.statementWithEmptyBodyIncludeOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "m_reportEmptyBlocks");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EmptyStatementVisitor();
    }

    private class EmptyStatementVisitor extends BaseInspectionVisitor {
        @Override
        public void visitDoWhileStatement(@Nonnull PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            checkLoopStatement(statement);
        }

        @Override
        public void visitWhileStatement(@Nonnull PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            checkLoopStatement(statement);
        }

        @Override
        public void visitForStatement(@Nonnull PsiForStatement statement) {
            super.visitForStatement(statement);
            checkLoopStatement(statement);
        }

        @Override
        public void visitForeachStatement(@Nonnull PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            checkLoopStatement(statement);
        }

        private void checkLoopStatement(PsiLoopStatement statement) {
     /* if (JspPsiUtil.isInJspFile(statement)) {
        return;
      }  */
            PsiStatement body = statement.getBody();
            if (body == null || !isEmpty(body)) {
                return;
            }
            registerStatementError(statement);
        }

        @Override
        public void visitIfStatement(@Nonnull PsiIfStatement statement) {
            super.visitIfStatement(statement);
      /*if (JspPsiUtil.isInJspFile(statement)) {
        return;
      } */
            PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch != null && isEmpty(thenBranch)) {
                registerStatementError(statement);
                return;
            }
            PsiStatement elseBranch = statement.getElseBranch();
            if (elseBranch != null && isEmpty(elseBranch)) {
                PsiElement elseToken = statement.getElseElement();
                if (elseToken == null) {
                    return;
                }
                registerError(elseToken);
            }
        }

        @Override
        public void visitSwitchStatement(PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
     /* if (JspPsiUtil.isInJspFile(statement)) {
        return;
      } */
            PsiCodeBlock body = statement.getBody();
            if (body == null || !isEmpty(body)) {
                return;
            }
            registerStatementError(statement);
        }

        private boolean isEmpty(PsiElement body) {
            if (body instanceof PsiEmptyStatement) {
                return true;
            }
            else if (body instanceof PsiBlockStatement) {
                PsiBlockStatement block = (PsiBlockStatement) body;
                return isEmpty(block.getCodeBlock());
            }
            else if (m_reportEmptyBlocks && body instanceof PsiCodeBlock) {
                PsiCodeBlock codeBlock = (PsiCodeBlock) body;
                PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length == 0) {
                    return true;
                }
                for (PsiStatement statement : statements) {
                    if (!isEmpty(statement)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }
}