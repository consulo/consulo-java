/*
 * Copyright 2006-201@ Bas Leijdekkers
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

import com.intellij.java.impl.ig.fixes.AddThisQualifierFix;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class UnqualifiedFieldAccessInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unqualifiedFieldAccessDisplayName();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnqualifiedFieldAccessVisitor();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.unqualifiedFieldAccessProblemDescriptor().get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new AddThisQualifierFix();
    }

    private static class UnqualifiedFieldAccessVisitor extends BaseInspectionVisitor {

        @Override
        public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            PsiExpression qualifierExpression = expression.getQualifierExpression();
            if (qualifierExpression != null) {
                return;
            }
            PsiReferenceParameterList parameterList = expression.getParameterList();
            if (parameterList == null) {
                return;
            }
            if (parameterList.getTypeArguments().length > 0) {
                // optimization: reference with type arguments are
                // definitely not references to fields.
                return;
            }
            PsiElement element = expression.resolve();
            if (!(element instanceof PsiField)) {
                return;
            }
            PsiField field = (PsiField) element;
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            PsiClass containingClass = field.getContainingClass();
            if (containingClass instanceof PsiAnonymousClass) {
                return;
            }
            registerError(expression);
        }
    }
}