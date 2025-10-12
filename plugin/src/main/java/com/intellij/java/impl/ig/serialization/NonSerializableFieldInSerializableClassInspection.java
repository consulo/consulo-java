/*
 * Copyright 2006-2012 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.java.impl.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.intellij.java.impl.ig.psiutils.SerializationUtils;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class NonSerializableFieldInSerializableClassInspection extends SerializableInspection {
    @SuppressWarnings({"PublicField"})
    public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.nonSerializableFieldInSerializableClassDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.nonSerializableFieldInSerializableClassProblemDescriptor().get();
    }

    @Override
    protected JComponent[] createAdditionalOptions() {
        LocalizeValue message = InspectionGadgetsLocalize.ignoreIfAnnotatedBy();
        return new JComponent[]{SpecialAnnotationsUtil.createSpecialAnnotationsListControl(ignorableAnnotations, message.get())};
    }

    @Nonnull
    @Override
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        final PsiField field = (PsiField) infos[0];
        return AddToIgnoreIfAnnotatedByListQuickFix.build(field, ignorableAnnotations);
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new NonSerializableFieldInSerializableClassVisitor();
    }

    private class NonSerializableFieldInSerializableClassVisitor extends BaseInspectionVisitor {
        @Override
        public void visitField(@Nonnull PsiField field) {
            if (field.hasModifierProperty(PsiModifier.TRANSIENT) || field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            final PsiClass aClass = field.getContainingClass();
            if (aClass == null) {
                return;
            }
            if (ignoreAnonymousInnerClasses && aClass instanceof PsiAnonymousClass) {
                return;
            }
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            if (SerializationUtils.isProbablySerializable(field.getType())) {
                return;
            }
            final boolean hasWriteObject = SerializationUtils.hasWriteObject(aClass);
            if (hasWriteObject) {
                return;
            }
            if (isIgnoredSubclass(aClass)) {
                return;
            }
            if (AnnotationUtil.isAnnotated(field, ignorableAnnotations)) {
                return;
            }
            registerFieldError(field, field);
        }
    }
}