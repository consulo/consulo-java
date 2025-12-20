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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class ThrowCaughtLocallyInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean ignoreRethrownExceptions = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.throwCaughtLocallyDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.throwCaughtLocallyProblemDescriptor().get();
    }

    @Nullable
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.throwCaughtLocallyIgnoreOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreRethrownExceptions");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ThrowCaughtLocallyVisitor();
    }

    private class ThrowCaughtLocallyVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            PsiExpression exception = statement.getException();
            if (exception == null) {
                return;
            }
            PsiType exceptionType = exception.getType();
            if (exceptionType == null) {
                return;
            }
            PsiTryStatement containingTryStatement =
                PsiTreeUtil.getParentOfType(
                    statement,
                    PsiTryStatement.class
                );
            while (containingTryStatement != null) {
                PsiCodeBlock tryBlock =
                    containingTryStatement.getTryBlock();
                if (tryBlock == null) {
                    return;
                }
                if (PsiTreeUtil.isAncestor(tryBlock, statement, true)) {
                    PsiParameter[] catchBlockParameters =
                        containingTryStatement.getCatchBlockParameters();
                    for (PsiParameter parameter : catchBlockParameters) {
                        PsiType parameterType = parameter.getType();
                        if (!parameterType.isAssignableFrom(exceptionType)) {
                            continue;
                        }
                        if (ignoreRethrownExceptions) {
                            PsiCatchSection section =
                                (PsiCatchSection) parameter.getParent();
                            PsiCodeBlock catchBlock =
                                section.getCatchBlock();
                            if (isExceptionRethrown(parameter, catchBlock)) {
                                return;
                            }
                        }
                        PsiClass containingClass =
                            ClassUtils.getContainingClass(statement);
                        if (PsiTreeUtil.isAncestor(containingClass,
                            containingTryStatement, true
                        )) {
                            registerStatementError(statement);
                            return;
                        }
                    }
                }
                containingTryStatement =
                    PsiTreeUtil.getParentOfType(
                        containingTryStatement,
                        PsiTryStatement.class
                    );
            }
        }

        private boolean isExceptionRethrown(
            PsiParameter parameter,
            PsiCodeBlock catchBlock
        ) {
            PsiStatement[] statements = catchBlock.getStatements();
            if (statements.length <= 0) {
                return false;
            }
            PsiStatement lastStatement =
                statements[statements.length - 1];
            if (!(lastStatement instanceof PsiThrowStatement)) {
                return false;
            }
            PsiThrowStatement throwStatement =
                (PsiThrowStatement) lastStatement;
            PsiExpression expression = throwStatement.getException();
            if (!(expression instanceof PsiReferenceExpression)) {
                return false;
            }
            PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) expression;
            PsiElement element = referenceExpression.resolve();
            return parameter.equals(element);
        }
    }
}