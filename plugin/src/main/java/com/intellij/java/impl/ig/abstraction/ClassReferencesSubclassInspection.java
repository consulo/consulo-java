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
package com.intellij.java.impl.ig.abstraction;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiNamedElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class ClassReferencesSubclassInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.classReferencesSubclassDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        PsiNamedElement element = (PsiNamedElement) infos[0];
        String containingClassName = element.getName();
        Boolean isAnonymous = (Boolean) infos[1];
        if (isAnonymous.booleanValue()) {
            return InspectionGadgetsLocalize.classReferencesSubclassProblemDescriptorAnonymous().get();
        }
        return InspectionGadgetsLocalize.classReferencesSubclassProblemDescriptor(containingClassName).get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassReferencesSubclassVisitor();
    }

    private static class ClassReferencesSubclassVisitor extends BaseInspectionVisitor {
        @Override
        public void visitVariable(@Nonnull PsiVariable variable) {
            PsiTypeElement typeElement = variable.getTypeElement();
            checkTypeElement(typeElement);
        }

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            PsiTypeElement typeElement = method.getReturnTypeElement();
            checkTypeElement(typeElement);
        }

        @Override
        public void visitInstanceOfExpression(
            @Nonnull PsiInstanceOfExpression expression
        ) {
            PsiTypeElement typeElement = expression.getCheckType();
            checkTypeElement(typeElement);
        }

        @Override
        public void visitTypeCastExpression(
            @Nonnull PsiTypeCastExpression expression
        ) {
            PsiTypeElement typeElement = expression.getCastType();
            checkTypeElement(typeElement);
        }

        @Override
        public void visitClassObjectAccessExpression(
            @Nonnull PsiClassObjectAccessExpression expression
        ) {
            PsiTypeElement typeElement = expression.getOperand();
            checkTypeElement(typeElement);
        }

        private void checkTypeElement(PsiTypeElement typeElement) {
            if (typeElement == null) {
                return;
            }
            PsiType type = typeElement.getType();
            PsiType componentType = type.getDeepComponentType();
            if (!(componentType instanceof PsiClassType)) {
                return;
            }
            PsiClassType classType = (PsiClassType) componentType;
            PsiClass aClass = classType.resolve();
            if (aClass instanceof PsiTypeParameter) {
                return;
            }
            PsiClass parentClass =
                ClassUtils.getContainingClass(typeElement);
            if (!isSubclass(aClass, parentClass)) {
                return;
            }
            registerError(typeElement, parentClass, Boolean.FALSE);
        }

        private static boolean isSubclass(
            @Nullable PsiClass childClass,
            @Nullable PsiClass parent
        ) {
            if (childClass == null) {
                return false;
            }
            if (parent == null) {
                return false;
            }
            return childClass.isInheritor(parent, true);
        }
    }
}