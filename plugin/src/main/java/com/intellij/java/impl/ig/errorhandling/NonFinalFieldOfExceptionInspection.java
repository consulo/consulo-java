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
package com.intellij.java.impl.ig.errorhandling;

import com.intellij.java.impl.ig.fixes.MakeFieldFinalFix;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class NonFinalFieldOfExceptionInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.nonFinalFieldOfExceptionDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.nonFinalFieldOfExceptionProblemDescriptor().get();
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        PsiField field = (PsiField) infos[0];
        return MakeFieldFinalFix.buildFix(field);
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new NonFinalFieldOfExceptionVisitor();
    }

    private static class NonFinalFieldOfExceptionVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitField(PsiField field) {
            super.visitField(field);
            if (field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_LANG_EXCEPTION)) {
                return;
            }
            registerFieldError(field, field);
        }
    }
}