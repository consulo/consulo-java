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
package com.intellij.java.impl.ig.finalization;

import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;

@ExtensionImpl
public class FinalizeInspection extends BaseInspection {
    @SuppressWarnings("PublicField")
    public boolean ignoreTrivialFinalizers = true;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "FinalizeDeclaration";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.finalizeDeclarationDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.finalizeDeclarationProblemDescriptor().get();
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.ignoreTrivialFinalizersOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "ignoreTrivialFinalizers");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new FinalizeDeclaredVisitor();
    }

    private class FinalizeDeclaredVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            //note: no call to super;
            final String methodName = method.getName();
            if (!HardcodedMethodConstants.FINALIZE.equals(methodName)) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != 0) {
                return;
            }
            if (ignoreTrivialFinalizers && isTrivial(method)) {
                return;
            }
            registerMethodError(method);
        }

        private boolean isTrivial(PsiMethod method) {
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return true;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements.length == 0) {
                return true;
            }
            final Project project = method.getProject();
            final JavaPsiFacade psiFacade =
                JavaPsiFacade.getInstance(project);
            final PsiConstantEvaluationHelper evaluationHelper =
                psiFacade.getConstantEvaluationHelper();
            for (PsiStatement statement : statements) {
                if (!(statement instanceof PsiIfStatement)) {
                    return false;
                }
                final PsiIfStatement ifStatement =
                    (PsiIfStatement) statement;
                final PsiExpression condition = ifStatement.getCondition();
                final Object result =
                    evaluationHelper.computeConstantExpression(condition);
                if (result == null || !result.equals(Boolean.FALSE)) {
                    return false;
                }
            }
            return true;
        }
    }
}