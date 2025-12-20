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

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteUnnecessaryStatementFix;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;

@ExtensionImpl
public class UnnecessaryReturnInspection extends BaseInspection {
    @SuppressWarnings("PublicField")
    public boolean ignoreInThenBranch = false;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "UnnecessaryReturnStatement";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unnecessaryReturnDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return (Boolean) infos[0]
            ? InspectionGadgetsLocalize.unnecessaryReturnConstructorProblemDescriptor().get()
            : InspectionGadgetsLocalize.unnecessaryReturnProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.unnecessaryReturnOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreInThenBranch");
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new DeleteUnnecessaryStatementFix("return");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryReturnVisitor();
    }

    private class UnnecessaryReturnVisitor extends BaseInspectionVisitor {
        @Override
        public void visitReturnStatement(@Nonnull PsiReturnStatement statement) {
            super.visitReturnStatement(statement);
    /*  if (JspPsiUtil.isInJspFile(statement.getContainingFile())) {
        return;
      } */
            if (statement.getReturnValue() != null) {
                return;
            }
            PsiElement methodParent = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
            PsiCodeBlock codeBlock = null;
            boolean constructor;
            if (methodParent instanceof PsiMethod) {
                PsiMethod method = (PsiMethod) methodParent;
                codeBlock = method.getBody();
                constructor = method.isConstructor();
            }
            else if (methodParent instanceof PsiLambdaExpression) {
                constructor = false;
                PsiLambdaExpression lambdaExpression = (PsiLambdaExpression) methodParent;
                PsiElement lambdaBody = lambdaExpression.getBody();
                if (lambdaBody instanceof PsiCodeBlock) {
                    codeBlock = (PsiCodeBlock) lambdaBody;
                }
            }
            else {
                return;
            }
            if (codeBlock == null) {
                return;
            }
            if (!ControlFlowUtils.blockCompletesWithStatement(codeBlock, statement)) {
                return;
            }
            if (ignoreInThenBranch && isInThenBranch(statement)) {
                return;
            }
            registerStatementError(statement, Boolean.valueOf(constructor));
        }

        private boolean isInThenBranch(PsiStatement statement) {
            PsiIfStatement ifStatement =
                PsiTreeUtil.getParentOfType(statement, PsiIfStatement.class, true, PsiMethod.class, PsiLambdaExpression.class);
            if (ifStatement == null) {
                return false;
            }
            PsiStatement elseBranch = ifStatement.getElseBranch();
            return elseBranch != null && !PsiTreeUtil.isAncestor(elseBranch, statement, true);
        }
    }
}