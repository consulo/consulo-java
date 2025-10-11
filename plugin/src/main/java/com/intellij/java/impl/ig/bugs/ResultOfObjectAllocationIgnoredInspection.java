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

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionStatement;
import com.intellij.java.language.psi.PsiNewExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ResultOfObjectAllocationIgnoredInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.resultOfObjectAllocationIgnoredDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.resultOfObjectAllocationIgnoredProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ResultOfObjectAllocationIgnoredVisitor();
    }

    private static class ResultOfObjectAllocationIgnoredVisitor extends BaseInspectionVisitor {

        @Override
        public void visitExpressionStatement(@Nonnull PsiExpressionStatement statement) {
            super.visitExpressionStatement(statement);
            final PsiExpression expression = statement.getExpression();
            if (!(expression instanceof PsiNewExpression)) {
                return;
            }
            final PsiNewExpression newExpression = (PsiNewExpression) expression;
            final PsiExpression[] arrayDimensions = newExpression.getArrayDimensions();
            if (arrayDimensions.length != 0) {
                return;
            }
            if (newExpression.getArrayInitializer() != null) {
                return;
            }
            registerNewExpressionError(newExpression);
        }
    }
}