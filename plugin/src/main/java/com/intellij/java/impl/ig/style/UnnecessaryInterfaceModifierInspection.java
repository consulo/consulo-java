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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class UnnecessaryInterfaceModifierInspection extends BaseInspection {
    private static final Set<String> INTERFACE_REDUNDANT_MODIFIERS =
        new HashSet<String>(Arrays.asList(PsiModifier.ABSTRACT, PsiModifier.STATIC));
    private static final Set<String> INNER_CLASS_REDUNDANT_MODIFIERS =
        new HashSet<String>(Arrays.asList(PsiModifier.PUBLIC, PsiModifier.STATIC));
    private static final Set<String> INNER_INTERFACE_REDUNDANT_MODIFIERS =
        new HashSet<String>(Arrays.asList(PsiModifier.PUBLIC, PsiModifier.ABSTRACT, PsiModifier.STATIC));
    private static final Set<String> FIELD_REDUNDANT_MODIFIERS =
        new HashSet<String>(Arrays.asList(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL));
    private static final Set<String> METHOD_REDUNDANT_MODIFIERS =
        new HashSet<String>(Arrays.asList(PsiModifier.PUBLIC, PsiModifier.ABSTRACT));

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unnecessaryInterfaceModifierDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        final PsiModifierList modifierList = (PsiModifierList) infos[1];
        final PsiElement parent = modifierList.getParent();
        if (parent instanceof PsiClass) {
            final PsiClass aClass = (PsiClass) parent;
            final PsiClass containingClass = aClass.getContainingClass();
            if (containingClass != null) {
                return aClass.isInterface()
                    ? InspectionGadgetsLocalize.unnecessaryInterfaceModifierInnerInterfaceOfInterfaceProblemDescriptor().get()
                    : InspectionGadgetsLocalize.unnecessaryInterfaceModifierProblemDescriptor3().get();
            }
            else {
                return InspectionGadgetsLocalize.unnecessaryInterfaceModifierProblemDescriptor().get();
            }
        }
        else if (parent instanceof PsiMethod) {
            return InspectionGadgetsLocalize.unnecessaryInterfaceModifierProblemDescriptor2().get();
        }
        else {
            return InspectionGadgetsLocalize.unnecessaryInterfaceModifierProblemDescriptor4().get();
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryInterfaceModifierVisitor();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryInterfaceModifiersFix((String) infos[0]);
    }

    private static class UnnecessaryInterfaceModifiersFix extends InspectionGadgetsFix {
        private final String modifiersText;

        private UnnecessaryInterfaceModifiersFix(String modifiersText) {
            this.modifiersText = modifiersText;
        }

        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.smthUnnecessaryRemoveQuickfix(modifiersText);
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) {
            final PsiElement element = descriptor.getPsiElement();
            final PsiModifierList modifierList;
            if (element instanceof PsiModifierList) {
                modifierList = (PsiModifierList) element;
            }
            else {
                final PsiElement parent = element.getParent();
                if (!(parent instanceof PsiModifierList)) {
                    return;
                }
                modifierList = (PsiModifierList) parent;
            }
            modifierList.setModifierProperty(PsiModifier.STATIC, false);
            final PsiElement modifierOwner = modifierList.getParent();
            assert modifierOwner != null;
            if (modifierOwner instanceof PsiClass) {
                final PsiClass aClass = (PsiClass) modifierOwner;
                if (aClass.isInterface()) {
                    modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
                }
                final PsiClass containingClass = ClassUtils.getContainingClass(modifierOwner);
                if (containingClass != null && containingClass.isInterface()) {
                    // do the inner classes
                    modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
                }
            }
            else if (modifierOwner instanceof PsiMethod) {
                modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
                modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
            }
            else {
                modifierList.setModifierProperty(PsiModifier.PUBLIC, false);
                modifierList.setModifierProperty(PsiModifier.FINAL, false);
            }
        }
    }

    private static class UnnecessaryInterfaceModifierVisitor extends BaseInspectionVisitor {
        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            final PsiClass parent = ClassUtils.getContainingClass(aClass);
            if (parent != null && parent.isInterface()) {
                final PsiModifierList modifiers = aClass.getModifierList();
                if (aClass.isInterface()) {
                    checkForRedundantModifiers(modifiers, INNER_INTERFACE_REDUNDANT_MODIFIERS);
                }
                else {
                    checkForRedundantModifiers(modifiers, INNER_CLASS_REDUNDANT_MODIFIERS);
                }
            }
            else if (aClass.isInterface()) {
                final PsiModifierList modifiers = aClass.getModifierList();
                checkForRedundantModifiers(modifiers, INTERFACE_REDUNDANT_MODIFIERS);
            }
        }

        @Override
        public void visitField(@Nonnull PsiField field) {
            // don't call super, to keep this from drilling in
            final PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!containingClass.isInterface()) {
                return;
            }
            final PsiModifierList modifiers = field.getModifierList();
            checkForRedundantModifiers(modifiers, FIELD_REDUNDANT_MODIFIERS);
        }

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            // don't call super, to keep this from drilling in
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            if (!aClass.isInterface()) {
                return;
            }
            final PsiModifierList modifiers = method.getModifierList();
            checkForRedundantModifiers(modifiers, METHOD_REDUNDANT_MODIFIERS);
        }

        public void checkForRedundantModifiers(PsiModifierList list, Set<String> modifiers) {
            if (list == null) {
                return;
            }
            final PsiElement[] children = list.getChildren();
            final StringBuilder redundantModifiers = new StringBuilder();
            for (PsiElement child : children) {
                final String modifierText = child.getText();
                if (modifiers.contains(modifierText)) {
                    if (redundantModifiers.length() > 0) {
                        redundantModifiers.append(' ');
                    }
                    redundantModifiers.append(modifierText);
                }
            }
            for (PsiElement child : children) {
                if (modifiers.contains(child.getText())) {
                    registerError(child, redundantModifiers.toString(), list);
                }
            }
        }
    }
}
