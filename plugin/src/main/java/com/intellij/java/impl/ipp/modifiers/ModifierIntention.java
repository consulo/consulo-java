/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.ipp.modifiers;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.application.WriteAction;
import consulo.application.util.query.Query;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiBundle;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.util.collection.MultiMap;
import consulo.util.io.FileUtil;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.MagicConstant;

/**
 * @author Bas Leijdekkers
 */
abstract class ModifierIntention extends Intention implements LowPriorityAction {

    @Nonnull
    @Override
    protected final PsiElementPredicate getElementPredicate() {
        return new ModifierPredicate(getModifier());
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    protected final void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        final PsiMember member = (PsiMember) element.getParent();
        final PsiModifierList modifierList = member.getModifierList();
        if (modifierList == null) {
            return;
        }
        MultiMap<PsiElement, LocalizeValue> conflicts = checkForConflicts(member);
        final boolean conflictsDialogOK;
        if (conflicts.isEmpty()) {
            conflictsDialogOK = true;
        }
        else {
            ConflictsDialog conflictsDialog = new ConflictsDialog(
                member.getProject(),
                conflicts,
                () -> WriteAction.run(() -> modifierList.setModifierProperty(getModifier(), true))
            );
            conflictsDialog.show();
            conflictsDialogOK = conflictsDialog.isOK();
        }

        if (conflictsDialogOK) {
            WriteAction.run(() -> modifierList.setModifierProperty(getModifier(), true));
        }
    }

    private MultiMap<PsiElement, LocalizeValue> checkForConflicts(@Nonnull PsiMember member) {
        if (member instanceof PsiClass && getModifier().equals(PsiModifier.PUBLIC)) {
            final PsiClass aClass = (PsiClass) member;
            final PsiElement parent = aClass.getParent();
            if (!(parent instanceof PsiJavaFile)) {
                return MultiMap.empty();
            }
            final PsiJavaFile javaFile = (PsiJavaFile) parent;
            final String name = FileUtil.getNameWithoutExtension(javaFile.getName());
            final String className = aClass.getName();
            if (name.equals(className)) {
                return MultiMap.empty();
            }
            MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
            conflicts.putValue(
                aClass,
                IntentionPowerPackLocalize.zeroIsDeclaredIn1ButWhenPublicShouldBeDeclaredInAFileNamed2(
                    RefactoringUIUtil.getDescription(aClass, false),
                    RefactoringUIUtil.getDescription(javaFile, false),
                    CommonRefactoringUtil.htmlEmphasize(className + ".java")
                )
            );
            return conflicts;
        }
        final PsiModifierList modifierList = member.getModifierList();
        if (modifierList == null || modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
            return MultiMap.empty();
        }
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
        if (member instanceof PsiMethod) {
            final PsiMethod method = (PsiMethod) member;
            SuperMethodsSearch.search(method, method.getContainingClass(), true, false).forEach(methodSignature -> {
                final PsiMethod superMethod = methodSignature.getMethod();
                if (!hasCompatibleVisibility(superMethod, true)) {
                    conflicts.putValue(
                        superMethod,
                        IntentionPowerPackLocalize.zeroWillHaveIncompatibleAccessPrivilegesWithSuper1(
                            RefactoringUIUtil.getDescription(method, false),
                            RefactoringUIUtil.getDescription(superMethod, true)
                        )
                    );
                }
                return true;
            });
            OverridingMethodsSearch.search(method).forEach(overridingMethod -> {
                if (!isVisibleFromOverridingMethod(method, overridingMethod)) {
                    conflicts.putValue(
                        overridingMethod,
                        IntentionPowerPackLocalize.zeroWillNoLongerBeVisibleFromOverriding1(
                            RefactoringUIUtil.getDescription(method, false),
                            RefactoringUIUtil.getDescription(overridingMethod, true)
                        )
                    );
                }
                else if (!hasCompatibleVisibility(overridingMethod, false)) {
                    conflicts.putValue(
                        overridingMethod,
                        IntentionPowerPackLocalize.zeroWillHaveIncompatibleAccessPrivilegesWithOverriding1(
                            RefactoringUIUtil.getDescription(method, false),
                            RefactoringUIUtil.getDescription(overridingMethod, true)
                        )
                    );
                }
                return false;
            });
        }
        final PsiModifierList modifierListCopy = (PsiModifierList) modifierList.copy();
        modifierListCopy.setModifierProperty(getModifier(), true);
        final Query<PsiReference> search = ReferencesSearch.search(member, member.getResolveScope());
        search.forEach(reference -> {
            final PsiElement element = reference.getElement();
            if (JavaResolveUtil.isAccessible(member, member.getContainingClass(), modifierListCopy, element, null, null)) {
                return true;
            }
            final PsiElement context = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiField.class, PsiClass.class, PsiFile.class);
            if (context == null) {
                return true;
            }
            conflicts.putValue(
                element,
                RefactoringLocalize.zeroWith1VisibilityIsNotAccessibleFrom2(
                    RefactoringUIUtil.getDescription(member, false),
                    PsiBundle.visibilityPresentation(getModifier()),
                    RefactoringUIUtil.getDescription(context, true)
                )
            );
            return true;
        });
        return conflicts;
    }

    private boolean hasCompatibleVisibility(PsiMethod method, boolean isSuper) {
        if (getModifier().equals(PsiModifier.PRIVATE)) {
            return false;
        }
        else if (getModifier().equals(PsiModifier.PACKAGE_LOCAL)) {
            if (isSuper) {
                return !(method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED));
            }
            return true;
        }
        else if (getModifier().equals(PsiModifier.PROTECTED)) {
            if (isSuper) {
                return !method.hasModifierProperty(PsiModifier.PUBLIC);
            }
            else {
                return method.hasModifierProperty(PsiModifier.PROTECTED) || method.hasModifierProperty(PsiModifier.PUBLIC);
            }
        }
        else if (getModifier().equals(PsiModifier.PUBLIC)) {
            if (!isSuper) {
                return method.hasModifierProperty(PsiModifier.PUBLIC);
            }
            return true;
        }
        throw new AssertionError();
    }

    private boolean isVisibleFromOverridingMethod(PsiMethod method, PsiMethod overridingMethod) {
        final PsiModifierList modifierListCopy = (PsiModifierList) method.getModifierList().copy();
        modifierListCopy.setModifierProperty(getModifier(), true);
        return JavaResolveUtil.isAccessible(method, method.getContainingClass(), modifierListCopy, overridingMethod, null, null);
    }

    @VisibilityConstant
    protected abstract String getModifier();

    @MagicConstant(stringValues = {PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE, PsiModifier.PACKAGE_LOCAL})
    @interface VisibilityConstant {
    }
}
