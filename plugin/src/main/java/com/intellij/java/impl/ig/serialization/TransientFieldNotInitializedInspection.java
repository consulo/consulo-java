/*
 * Copyright 2007 Bas Leijdekkers
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
package com.intellij.java.impl.ig.serialization;

import com.intellij.java.impl.ig.psiutils.InitializationUtils;
import com.intellij.java.impl.ig.psiutils.SerializationUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class TransientFieldNotInitializedInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.transientFieldNotInitializedDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.transientFieldNotInitializedProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ReadObjectInitializationVisitor();
    }

    private static class ReadObjectInitializationVisitor extends BaseInspectionVisitor {
        @Override
        public void visitField(PsiField field) {
            super.visitField(field);
            if (!field.hasModifierProperty(PsiModifier.TRANSIENT)) {
                return;
            }
            PsiClass containingClass = field.getContainingClass();
            if (!SerializationUtils.isSerializable(containingClass)) {
                return;
            }
            PsiExpression initializer = field.getInitializer();
            if (initializer == null &&
                !isInitializedInInitializer(field, containingClass) &&
                !isInitializedInConstructors(field, containingClass)) {
                return;
            }
            if (SerializationUtils.hasReadObject(containingClass)) {
                return;
            }
            registerFieldError(field);
        }

        private static boolean isInitializedInConstructors(@Nonnull PsiField field, @Nonnull PsiClass aClass) {
            PsiMethod[] constructors = aClass.getConstructors();
            if (constructors.length == 0) {
                return false;
            }
            for (PsiMethod constructor : constructors) {
                if (!InitializationUtils.methodAssignsVariableOrFails(
                    constructor, field)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean isInitializedInInitializer(@Nonnull PsiField field, @Nonnull PsiClass aClass) {
            PsiClassInitializer[] initializers = aClass.getInitializers();
            for (PsiClassInitializer initializer : initializers) {
                if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
                    continue;
                }
                PsiCodeBlock body = initializer.getBody();
                if (InitializationUtils.blockAssignsVariableOrFails(body, field)) {
                    return true;
                }
            }
            return false;
        }
    }
}