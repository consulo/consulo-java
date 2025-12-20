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
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class InstanceofCatchParameterInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.instanceofCatchParameterDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.instanceofCatchParameterProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InstanceofCatchParameterVisitor();
    }

    private static class InstanceofCatchParameterVisitor extends BaseInspectionVisitor {
        @Override
        public void visitInstanceOfExpression(@Nonnull PsiInstanceOfExpression exp) {
            super.visitInstanceOfExpression(exp);
            if (!ControlFlowUtils.isInCatchBlock(exp)) {
                return;
            }
            PsiExpression operand = exp.getOperand();
            if (!(operand instanceof PsiReferenceExpression)) {
                return;
            }
            PsiReferenceExpression ref = (PsiReferenceExpression) operand;
            PsiElement referent = ref.resolve();
            if (!(referent instanceof PsiParameter)) {
                return;
            }
            PsiParameter parameter = (PsiParameter) referent;
            if (!(parameter.getDeclarationScope() instanceof PsiCatchSection)) {
                return;
            }
            registerError(exp);
        }
    }
}
