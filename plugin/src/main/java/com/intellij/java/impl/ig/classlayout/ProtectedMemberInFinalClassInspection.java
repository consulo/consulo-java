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

import com.intellij.java.impl.ig.fixes.RemoveModifierFix;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessToken;
import consulo.application.WriteAction;
import consulo.application.util.function.Processor;
import consulo.application.util.query.Query;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.PsiBundle;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ProtectedMemberInFinalClassInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.protectedMemberInFinalClassDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.protectedMemberInFinalClassProblemDescriptor().get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new RemoveModifierFix((String) infos[0]);
    }

    @Nonnull
    @Override
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        return new InspectionGadgetsFix[]{
            new RemoveModifierFix((String) infos[0]),
            new MakePrivateFix()
        };
    }

    private static class MakePrivateFix extends InspectionGadgetsFix {
        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.makePrivateQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            final PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMember)) {
                return;
            }
            final PsiMember member = (PsiMember) grandParent;
            final PsiModifierList modifierList = member.getModifierList();
            if (modifierList == null) {
                return;
            }
            final MultiMap<PsiElement, String> conflicts = new MultiMap();
            if (member instanceof PsiMethod) {
                final PsiMethod method = (PsiMethod) member;
                SuperMethodsSearch.search(method, method.getContainingClass(), true, false).forEach(
                    new Processor<MethodSignatureBackedByPsiMethod>() {
                        @Override
                        public boolean process(MethodSignatureBackedByPsiMethod methodSignature) {
                            final PsiMethod superMethod = methodSignature.getMethod();
                            conflicts.putValue(
                                superMethod,
                                InspectionGadgetsLocalize.zeroWillHaveIncompatibleAccessPrivilegesWithSuper1(
                                    RefactoringUIUtil.getDescription(method, false),
                                    RefactoringUIUtil.getDescription(superMethod, true)
                                ).get()
                            );
                            return true;
                        }
                    });
                OverridingMethodsSearch.search(method).forEach(new Processor<PsiMethod>() {
                    @Override
                    public boolean process(PsiMethod overridingMethod) {
                        conflicts.putValue(
                            overridingMethod,
                            InspectionGadgetsLocalize.zeroWillNoLongerBeVisibleFromOverriding1(
                                RefactoringUIUtil.getDescription(method, false),
                                RefactoringUIUtil.getDescription(overridingMethod, true)
                            ).get()
                        );
                        return false;
                    }
                });
            }
            final PsiModifierList modifierListCopy = (PsiModifierList) modifierList.copy();
            modifierListCopy.setModifierProperty(PsiModifier.PRIVATE, true);
            final Query<PsiReference> search = ReferencesSearch.search(member, member.getResolveScope());
            search.forEach(new Processor<PsiReference>() {
                @Override
                public boolean process(PsiReference reference) {
                    final PsiElement element = reference.getElement();
                    if (!JavaResolveUtil.isAccessible(member, member.getContainingClass(), modifierListCopy, element, null, null)) {
                        final PsiElement context =
                            PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiField.class, PsiClass.class, PsiFile.class);
                        conflicts.putValue(
                            element,
                            RefactoringLocalize.zeroWith1VisibilityIsNotAccessibleFrom2(
                                RefactoringUIUtil.getDescription(member, false),
                                PsiBundle.visibilityPresentation(PsiModifier.PRIVATE),
                                RefactoringUIUtil.getDescription(context, true)
                            ).get()
                        );
                    }
                    return true;
                }
            });
            final boolean conflictsDialogOK;
            if (conflicts.isEmpty()) {
                conflictsDialogOK = true;
            }
            else {
                if (!isOnTheFly()) {
                    return;
                }
                final ConflictsDialog conflictsDialog = new ConflictsDialog(member.getProject(), conflicts, new Runnable() {
                    @Override
                    public void run() {
                        final AccessToken token = WriteAction.start();
                        try {
                            modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
                        }
                        finally {
                            token.finish();
                        }
                    }
                });
                conflictsDialog.show();
                conflictsDialogOK = conflictsDialog.isOK();
            }
            if (conflictsDialogOK) {
                modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ProtectedMemberInFinalClassVisitor();
    }

    private static class ProtectedMemberInFinalClassVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            if (!method.hasModifierProperty(PsiModifier.PROTECTED)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null || !containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            if (MethodUtils.hasSuper(method)) {
                return;
            }
            registerModifierError(PsiModifier.PROTECTED, method, PsiModifier.PROTECTED);
        }

        @Override
        public void visitField(@Nonnull PsiField field) {
            if (!field.hasModifierProperty(PsiModifier.PROTECTED)) {
                return;
            }
            final PsiClass containingClass = field.getContainingClass();
            if (containingClass == null || !containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            registerModifierError(PsiModifier.PROTECTED, field, PsiModifier.PROTECTED);
        }
    }
}