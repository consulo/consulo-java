/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ig.psiutils.UtilityClassUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class UtilityClassWithPublicConstructorInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.utilityClassWithPublicConstructorDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.utilityClassWithPublicConstructorProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        PsiClass psiClass = (PsiClass) infos[0];
        if (psiClass.getConstructors().length > 1) {
            return new UtilityClassWithPublicConstructorFix(true);
        }
        else {
            return new UtilityClassWithPublicConstructorFix(false);
        }
    }

    private static class UtilityClassWithPublicConstructorFix
        extends InspectionGadgetsFix {

        private final boolean m_multipleConstructors;

        UtilityClassWithPublicConstructorFix(boolean multipleConstructors) {
            super();
            m_multipleConstructors = multipleConstructors;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.utilityClassWithPublicConstructorMakeQuickfix(m_multipleConstructors ? 1 : 2);
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement classNameIdentifier = descriptor.getPsiElement();
            PsiClass psiClass = (PsiClass) classNameIdentifier.getParent();
            if (psiClass == null) {
                return;
            }
            PsiMethod[] constructors = psiClass.getConstructors();
            for (PsiMethod constructor : constructors) {
                PsiModifierList modifierList =
                    constructor.getModifierList();
                modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new StaticClassWithPublicConstructorVisitor();
    }

    private static class StaticClassWithPublicConstructorVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // no call to super, so that it doesn't drill down to inner classes
            if (!UtilityClassUtil.isUtilityClass(aClass)) {
                return;
            }
            if (!hasPublicConstructor(aClass)) {
                return;
            }
            registerClassError(aClass, aClass);
        }

        private static boolean hasPublicConstructor(PsiClass aClass) {
            PsiMethod[] constructors = aClass.getConstructors();
            for (PsiMethod constructor : constructors) {
                if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
                    return true;
                }
            }
            return false;
        }
    }
}