/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ReturnThisInspection extends BaseInspection {
    @Nonnull
    public String getID() {
        return "ReturnOfThis";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.returnThisDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.returnThisProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ReturnThisVisitor();
    }

    private static class ReturnThisVisitor extends BaseInspectionVisitor {
        @Override
        public void visitThisExpression(@Nonnull PsiThisExpression thisValue) {
            super.visitThisExpression(thisValue);
            if (thisValue.getQualifier() != null) {
                return;
            }
            PsiElement parent = thisValue.getParent();
            while (parent instanceof PsiParenthesizedExpression ||
                parent instanceof PsiConditionalExpression ||
                parent instanceof PsiTypeCastExpression) {
                parent = parent.getParent();
            }
            if (!(parent instanceof PsiReturnStatement)) {
                return;
            }
            registerError(thisValue);
        }
    }
}