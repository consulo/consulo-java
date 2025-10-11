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
package com.intellij.java.impl.ig.initialization;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class OverriddenMethodCallDuringObjectConstructionInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.overriddenMethodCallInConstructorDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.overriddenMethodCallInConstructorProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new OverriddenMethodCallInConstructorVisitor();
    }

    private static class OverriddenMethodCallInConstructorVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!MethodCallUtils.isCallDuringObjectConstruction(expression)) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier != null) {
                if (!(qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression)) {
                    return;
                }
            }
            final PsiClass containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
            if (containingClass == null || containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiMethod calledMethod = expression.resolveMethod();
            if (calledMethod == null || !PsiUtil.canBeOverriden(calledMethod)) {
                return;
            }
            final PsiClass calledMethodClass = calledMethod.getContainingClass();
            if (!InheritanceUtil.isInheritorOrSelf(containingClass, calledMethodClass, true)) {
                return;
            }
            if (!MethodUtils.isOverriddenInHierarchy(calledMethod, containingClass)) {
                return;
            }
            registerMethodCallError(expression);
        }
    }
}