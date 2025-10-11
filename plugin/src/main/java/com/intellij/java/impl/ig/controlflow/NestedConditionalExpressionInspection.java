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

import com.intellij.java.language.psi.PsiConditionalExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class NestedConditionalExpressionInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.nestedConditionalExpressionDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.nestedConditionalExpressionProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NestedConditionalExpressionVisitor();
    }

    private static class NestedConditionalExpressionVisitor extends BaseInspectionVisitor {
        @Override
        public void visitConditionalExpression(@Nonnull PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            if (PsiTreeUtil.getParentOfType(expression, PsiConditionalExpression.class) == null) {
                return;
            }
            registerError(expression);
        }
    }
}