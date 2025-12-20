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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.java.analysis.impl.codeInspection.EquivalenceChecker;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class CollectionAddedToSelfInspection extends BaseInspection {

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.collectionAddedToSelfDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.collectionAddedToSelfProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new CollectionAddedToSelfVisitor();
    }

    private static class CollectionAddedToSelfVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(
            @Nonnull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            PsiReferenceExpression methodExpression =
                call.getMethodExpression();
            String methodName =
                methodExpression.getReferenceName();
            if (!"put".equals(methodName) && !"set".equals(methodName) &&
                !"add".equals(methodName)) {
                return;
            }
            PsiExpression qualifier =
                methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return;
            }
            PsiElement referent = ((PsiReference) qualifier).resolve();
            if (!(referent instanceof PsiVariable)) {
                return;
            }
            PsiExpressionList argumentList = call.getArgumentList();
            boolean hasMatchingArg = false;
            PsiExpression[] args = argumentList.getExpressions();
            for (PsiExpression arg : args) {
                if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(qualifier, arg)) {
                    hasMatchingArg = true;
                }
            }
            if (!hasMatchingArg) {
                return;
            }
            PsiType qualifierType = qualifier.getType();
            if (!(qualifierType instanceof PsiClassType)) {
                return;
            }
            PsiClassType classType = (PsiClassType) qualifierType;
            PsiClass qualifierClass = classType.resolve();
            if (qualifierClass == null) {
                return;
            }
            if (!InheritanceUtil.isInheritor(qualifierClass, CommonClassNames.JAVA_UTIL_COLLECTION)
                && !InheritanceUtil.isInheritor(qualifierClass, CommonClassNames.JAVA_UTIL_MAP)) {
                return;
            }
            registerError(qualifier);
        }
    }
}