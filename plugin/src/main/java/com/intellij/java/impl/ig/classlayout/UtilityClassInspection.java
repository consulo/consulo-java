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
package com.intellij.java.impl.ig.classlayout;

import com.intellij.java.impl.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.java.impl.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.intellij.java.impl.ig.psiutils.UtilityClassUtil;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;

@ExtensionImpl
public class UtilityClassInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.utilityClassDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.utilityClassProblemDescriptor().get();
    }

    @Nonnull
    @Override
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        return AddToIgnoreIfAnnotatedByListQuickFix.build((PsiModifierListOwner) infos[0], ignorableAnnotations);
    }

    @Override
    public JComponent createOptionsPanel() {
        return SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
            ignorableAnnotations,
            InspectionGadgetsLocalize.ignoreIfAnnotatedBy().get()
        );
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UtilityClassVisitor();
    }

    private class UtilityClassVisitor extends BaseInspectionVisitor {

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (!UtilityClassUtil.isUtilityClass(aClass)) {
                return;
            }
            if (AnnotationUtil.isAnnotated(aClass, ignorableAnnotations)) {
                return;
            }
            registerClassError(aClass, aClass);
        }
    }
}