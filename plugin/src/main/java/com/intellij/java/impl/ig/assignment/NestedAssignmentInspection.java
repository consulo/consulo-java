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
package com.intellij.java.impl.ig.assignment;

import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiExpressionListStatement;
import com.intellij.java.language.psi.PsiExpressionStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class NestedAssignmentInspection extends BaseInspection {
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.nestedAssignmentDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.nestedAssignmentProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NestedAssignmentVisitor();
    }

    private static class NestedAssignmentVisitor extends BaseInspectionVisitor {

        @Override
        public void visitAssignmentExpression(
            @Nonnull PsiAssignmentExpression expression
        ) {
            super.visitAssignmentExpression(expression);
            final PsiElement parent = expression.getParent();
            if (parent == null) {
                return;
            }
            final PsiElement grandparent = parent.getParent();
            if (parent instanceof PsiExpressionStatement ||
                grandparent instanceof PsiExpressionListStatement) {
                return;
            }
            registerError(expression);
        }
    }
}