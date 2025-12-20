/*
 * Copyright 2008-2011 Bas Leijdekkers
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
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class UnnecessarySuperQualifierInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unnecessarySuperQualifierDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.unnecessarySuperQualifierProblemDescriptor().get();
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessarySuperQualifierFix();
    }

    private static class UnnecessarySuperQualifierFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.unnecessarySuperQualifierQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            element.delete();
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessarySuperQualifierVisitor();
    }

    private static class UnnecessarySuperQualifierVisitor extends BaseInspectionVisitor {
        @Override
        public void visitSuperExpression(PsiSuperExpression expression) {
            super.visitSuperExpression(expression);
            PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
            if (qualifier != null) {
                return;
            }
            PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiReferenceExpression)) {
                return;
            }
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) parent;
            PsiElement grandParent = referenceExpression.getParent();
            if (grandParent instanceof PsiMethodCallExpression methodCallExpression) {
                if (!hasUnnecessarySuperQualifier(methodCallExpression)) {
                    return;
                }
            }
            else {
                if (!hasUnnecessarySuperQualifier(referenceExpression)) {
                    return;
                }
            }
            registerError(expression, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
        }

        private static boolean hasUnnecessarySuperQualifier(PsiReferenceExpression referenceExpression) {
            PsiClass parentClass = PsiTreeUtil.getParentOfType(referenceExpression, PsiClass.class);
            if (parentClass == null) {
                return false;
            }
            PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiField)) {
                return false;
            }
            PsiField superField = (PsiField) target;
            PsiReferenceExpression copy = (PsiReferenceExpression) referenceExpression.copy();
            PsiElement qualifier = copy.getQualifier();
            if (qualifier == null) {
                return false;
            }
            qualifier.delete(); // remove super
            return superField == copy.resolve();
        }

        private static boolean hasUnnecessarySuperQualifier(PsiMethodCallExpression methodCallExpression) {
            PsiMethod superMethod = methodCallExpression.resolveMethod();
            if (superMethod == null) {
                return false;
            }
            // check that super.m() and m() resolve to the same method
            PsiMethodCallExpression copy = (PsiMethodCallExpression) methodCallExpression.copy();
            PsiReferenceExpression methodExpression = copy.getMethodExpression();
            PsiElement qualifier = methodExpression.getQualifier();
            if (qualifier == null) {
                return false;
            }
            qualifier.delete(); //remove super
            return superMethod == copy.resolveMethod();
        }
    }
}