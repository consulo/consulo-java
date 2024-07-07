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

import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import jakarta.annotation.Nonnull;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.*;
import consulo.application.AccessToken;
import consulo.application.WriteAction;
import consulo.project.Project;
import consulo.language.psi.*;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import consulo.language.psi.search.ReferencesSearch;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.application.util.function.Processor;
import consulo.application.util.query.Query;
import consulo.util.collection.MultiMap;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.impl.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.MethodUtils;

@ExtensionImpl
public class ProtectedMemberInFinalClassInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("protected.member.in.final.class.display.name");
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("protected.member.in.final.class.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new RemoveModifierFix((String)infos[0]);
  }

  @Nonnull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    return new InspectionGadgetsFix[] {
      new RemoveModifierFix((String)infos[0]),
      new MakePrivateFix()
    };
  }

  private static class MakePrivateFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message("make.private.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMember)) {
        return;
      }
      final PsiMember member = (PsiMember)grandParent;
      final PsiModifierList modifierList = member.getModifierList();
      if (modifierList == null) {
        return;
      }
      final MultiMap<PsiElement, String> conflicts = new MultiMap();
      if (member instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)member;
        SuperMethodsSearch.search(method, method.getContainingClass(), true, false).forEach(
        new Processor<MethodSignatureBackedByPsiMethod>() {
          @Override
          public boolean process(MethodSignatureBackedByPsiMethod methodSignature) {
            final PsiMethod superMethod = methodSignature.getMethod();
              conflicts.putValue(superMethod, InspectionGadgetsBundle.message(
                "0.will.have.incompatible.access.privileges.with.super.1",
                RefactoringUIUtil.getDescription(method, false),
                RefactoringUIUtil.getDescription(superMethod, true)));
            return true;
          }
        });
      OverridingMethodsSearch.search(method).forEach(new Processor<PsiMethod>() {
        @Override
        public boolean process(PsiMethod overridingMethod) {
            conflicts.putValue(overridingMethod, InspectionGadgetsBundle.message(
              "0.will.no.longer.be.visible.from.overriding.1",
              RefactoringUIUtil.getDescription(method, false),
              RefactoringUIUtil.getDescription(overridingMethod, true)));
          return false;
        }
      });
      }
      final PsiModifierList modifierListCopy = (PsiModifierList)modifierList.copy();
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
              RefactoringLocalize.zeroWith1VisibilityIsNotAccessibleFrom2(RefactoringUIUtil.getDescription(member, false),
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
      } else {
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