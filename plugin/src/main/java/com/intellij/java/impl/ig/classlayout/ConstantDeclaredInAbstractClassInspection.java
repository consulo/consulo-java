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
package com.intellij.java.impl.ig.classlayout;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ConstantDeclaredInAbstractClassInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.constantDeclaredInAbstractClassDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.constantDeclaredInAbstractClassProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConstantDeclaredInAbstractClassVisitor();
    }

    private static class ConstantDeclaredInAbstractClassVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitField(@Nonnull PsiField field) {
            //no call to super, so we don't drill into anonymous classes
            if (!field.hasModifierProperty(PsiModifier.STATIC) ||
                !field.hasModifierProperty(PsiModifier.PUBLIC) ||
                !field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (containingClass.isInterface() ||
                containingClass.isAnnotationType() ||
                containingClass.isEnum()) {
                return;
            }
            if (!containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            registerFieldError(field);
        }
    }
}