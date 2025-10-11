/*
 * Copyright 2005-2011 Bas Leijdekkers
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
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class SuspiciousToArrayCallInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.suspiciousToArrayCallDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        final PsiType type = (PsiType) infos[0];
        return InspectionGadgetsLocalize.suspiciousToArrayCallProblemDescriptor(type.getPresentableText()).get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SuspiciousToArrayCallVisitor();
    }

    private static class SuspiciousToArrayCallVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethodCallExpression(
            @Nonnull PsiMethodCallExpression expression
        ) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
            @NonNls final String methodName =
                methodExpression.getReferenceName();
            if (!"toArray".equals(methodName)) {
                return;
            }
            final PsiExpression qualifierExpression =
                methodExpression.getQualifierExpression();
            if (qualifierExpression == null) {
                return;
            }
            final PsiType type = qualifierExpression.getType();
            if (!(type instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType) type;
            final PsiClass aClass = classType.resolve();
            if (aClass == null ||
                !InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            final PsiExpression argument = arguments[0];
            checkCollectionAndArrayTypes(classType, argument, expression);
        }

        private void checkCollectionAndArrayTypes(
            @Nonnull PsiClassType collectionType,
            @Nonnull PsiExpression argument,
            @Nonnull PsiMethodCallExpression expression
        ) {
            final PsiType argumentType = argument.getType();
            if (!(argumentType instanceof PsiArrayType)) {
                return;
            }
            final PsiArrayType arrayType = (PsiArrayType) argumentType;
            final PsiType componentType = arrayType.getComponentType();
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiTypeCastExpression) {
                final PsiTypeCastExpression castExpression =
                    (PsiTypeCastExpression) parent;
                final PsiTypeElement castTypeElement =
                    castExpression.getCastType();
                if (castTypeElement == null) {
                    return;
                }
                final PsiType castType = castTypeElement.getType();
                if (!castType.equals(arrayType)) {
                    registerError(argument, arrayType.getComponentType());
                }
            }
            else {
                if (!collectionType.hasParameters()) {
                    return;
                }
                final PsiType[] parameters = collectionType.getParameters();
                if (parameters.length != 1) {
                    return;
                }
                final PsiType parameter = parameters[0];
                if (!componentType.isAssignableFrom(parameter)) {
                    registerError(argument, parameter);
                }
            }
        }
    }
}