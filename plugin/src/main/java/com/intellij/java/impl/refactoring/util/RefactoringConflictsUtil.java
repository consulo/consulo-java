/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.util;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.project.Project;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.*;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.PsiSearchScopeUtil;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.PsiUtilCore;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.usage.MoveRenameUsageInfo;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.usage.UsageInfo;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.MultiMap;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author anna
 * Date: 05-Oct-2009
 */
public class RefactoringConflictsUtil {
  private RefactoringConflictsUtil() { }

  public static void analyzeAccessibilityConflicts(@Nonnull Set<PsiMember> membersToMove,
                                                   @Nonnull PsiClass targetClass,
                                                   @Nonnull MultiMap<PsiElement, String> conflicts,
                                                   @Nullable String newVisibility) {
    analyzeAccessibilityConflicts(membersToMove, targetClass, conflicts, newVisibility, targetClass, null);
  }

  public static void analyzeAccessibilityConflicts(@Nonnull Set<PsiMember> membersToMove,
                                                   @Nullable PsiClass targetClass,
                                                   @Nonnull MultiMap<PsiElement, String> conflicts,
                                                   @Nullable String newVisibility,
                                                   @Nonnull PsiElement context,
                                                   @Nullable Set<PsiMethod> abstractMethods) {
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(newVisibility)) { //Still need to check for access object
      newVisibility = PsiModifier.PUBLIC;
    }

    for (PsiMember member : membersToMove) {
      checkUsedElements(member, member, membersToMove, abstractMethods, targetClass, context, conflicts);
      checkAccessibilityConflicts(member, newVisibility, targetClass, membersToMove, conflicts);
    }
  }

  public static void checkAccessibilityConflicts(@Nonnull PsiMember member,
                                                 @PsiModifier.ModifierConstant @Nullable String newVisibility,
                                                 @Nullable PsiClass targetClass,
                                                 @Nonnull Set<PsiMember> membersToMove,
                                                 @Nonnull MultiMap<PsiElement, String> conflicts) {
    PsiModifierList modifierListCopy = member.getModifierList();
    if (modifierListCopy != null) {
      modifierListCopy = (PsiModifierList)modifierListCopy.copy();
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass != null && containingClass.isInterface()) {
        VisibilityUtil.setVisibility(modifierListCopy, PsiModifier.PUBLIC);
      }
    }
    if (newVisibility != null && modifierListCopy != null) {
      try {
        VisibilityUtil.setVisibility(modifierListCopy, newVisibility);
      }
      catch (IncorrectOperationException ignore) { } // do nothing and hope for the best
    }

    checkAccessibilityConflicts(member, modifierListCopy, targetClass, membersToMove, conflicts);
  }

  public static void checkAccessibilityConflicts(@Nonnull PsiMember member,
                                                 @Nullable PsiModifierList modifierListCopy,
                                                 @Nullable PsiClass targetClass,
                                                 @Nonnull Set<PsiMember> membersToMove,
                                                 @Nonnull MultiMap<PsiElement, String> conflicts) {
    for (PsiReference psiReference : ReferencesSearch.search(member)) {
      checkAccessibilityConflicts(psiReference, member, modifierListCopy, targetClass, membersToMove, conflicts);
    }
  }

  public static void checkAccessibilityConflicts(@Nonnull PsiReference reference,
                                                 @Nonnull PsiMember member,
                                                 @Nullable PsiModifierList modifierListCopy,
                                                 @Nullable PsiClass targetClass,
                                                 @Nonnull Set<PsiMember> membersToMove,
                                                 @Nonnull MultiMap<PsiElement, String> conflicts) {
    JavaPsiFacade manager = JavaPsiFacade.getInstance(member.getProject());
    PsiElement ref = reference.getElement();
    if (!RefactoringHierarchyUtil.willBeInTargetClass(ref, membersToMove, targetClass, false)) {
      // check for target class accessibility
      if (targetClass != null && !manager.getResolveHelper().isAccessible(targetClass, targetClass.getModifierList(), ref, null, null)) {
        LocalizeValue message = RefactoringLocalize.zeroIs1AndWillNotBeAccessibleFrom2InTheTargetClass(
          RefactoringUIUtil.getDescription(targetClass, true),
          VisibilityUtil.getVisibilityStringToDisplay(targetClass),
          RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(ref), true)
        );
        conflicts.putValue(targetClass, CommonRefactoringUtil.capitalize(message.get()));
      }
      // check for member accessibility
      else if (!manager.getResolveHelper().isAccessible(member, modifierListCopy, ref, targetClass, null)) {
        LocalizeValue message = RefactoringLocalize.zeroIs1AndWillNotBeAccessibleFrom2InTheTargetClass(
          RefactoringUIUtil.getDescription(member, true),
          VisibilityUtil.toPresentableText(VisibilityUtil.getVisibilityModifier(modifierListCopy)),
          RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(ref), true)
        );
        conflicts.putValue(member, CommonRefactoringUtil.capitalize(message.get()));
      }
    }
  }

  public static void checkUsedElements(PsiMember member,
                                       PsiElement scope,
                                       @Nonnull Set<PsiMember> membersToMove,
                                       @Nullable Set<PsiMethod> abstractMethods,
                                       @Nullable PsiClass targetClass,
                                       @Nonnull PsiElement context,
                                       MultiMap<PsiElement, String> conflicts) {
    final Set<PsiMember> moving = new HashSet<PsiMember>(membersToMove);
    if (abstractMethods != null) {
      moving.addAll(abstractMethods);
    }
    if (scope instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)scope;
      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiMember) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, moving, targetClass, false)) {
          PsiExpression qualifier = refExpr.getQualifierExpression();
          PsiClass accessClass = (PsiClass)(qualifier != null ? PsiUtil.getAccessObjectClass(qualifier).getElement() : null);
          checkAccessibility((PsiMember)refElement, context, accessClass, member, conflicts);
        }
      }
    }
    else if (scope instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)scope;
      final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
      if (anonymousClass != null) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(anonymousClass, moving, targetClass, false)) {
          checkAccessibility(anonymousClass, context, anonymousClass, member, conflicts);
        }
      }
      else {
        final PsiMethod refElement = newExpression.resolveConstructor();
        if (refElement != null) {
          if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, moving, targetClass, false)) {
            checkAccessibility(refElement, context, null, member, conflicts);
          }
        }
      }
    }
    else if (scope instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement refExpr = (PsiJavaCodeReferenceElement)scope;
      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiMember) {
        if (!RefactoringHierarchyUtil.willBeInTargetClass(refElement, moving, targetClass, false)) {
          checkAccessibility((PsiMember)refElement, context, null, member, conflicts);
        }
      }
    }

    for (PsiElement child : scope.getChildren()) {
      if (child instanceof PsiWhiteSpace || child instanceof PsiComment) continue;
      checkUsedElements(member, child, membersToMove, abstractMethods, targetClass, context, conflicts);
    }
  }

  public static void checkAccessibility(PsiMember refMember,
                                        @Nonnull PsiElement newContext,
                                        @Nullable PsiClass accessClass,
                                        PsiMember member,
                                        MultiMap<PsiElement, String> conflicts) {
    if (!PsiUtil.isAccessible(refMember, newContext, accessClass)) {
      LocalizeValue message = RefactoringLocalize.zeroIs1AndWillNotBeAccessibleFrom2InTheTargetClass(
        RefactoringUIUtil.getDescription(refMember, true),
        VisibilityUtil.getVisibilityStringToDisplay(refMember),
        RefactoringUIUtil.getDescription(member, false)
      );
      conflicts.putValue(refMember, CommonRefactoringUtil.capitalize(message.get());
    }
    else if (newContext instanceof PsiClass && refMember instanceof PsiField && refMember.getContainingClass() == member.getContainingClass()) {
      final PsiField fieldInSubClass = ((PsiClass)newContext).findFieldByName(refMember.getName(), false);
      if (fieldInSubClass != null && fieldInSubClass != refMember) {
        conflicts.putValue(refMember, CommonRefactoringUtil.capitalize(RefactoringUIUtil.getDescription(fieldInSubClass, true) +
                                                                       " would hide " + RefactoringUIUtil.getDescription(refMember, true) +
                                                                       " which is used by moved " + RefactoringUIUtil.getDescription(member, false)));
      }
    }
  }

  public static void analyzeModuleConflicts(final Project project,
                                            final Collection<? extends PsiElement> scopes,
                                            final UsageInfo[] usages,
                                            final PsiElement target,
                                            final MultiMap<PsiElement,String> conflicts) {
    if (scopes == null) return;
    final VirtualFile vFile = PsiUtilCore.getVirtualFile(target);
    if (vFile == null) return;

    analyzeModuleConflicts(project, scopes, usages, vFile, conflicts);
  }

  public static void analyzeModuleConflicts(final Project project,
                                            final Collection<? extends PsiElement> scopes,
                                            final UsageInfo[] usages,
                                            final VirtualFile vFile,
                                            final MultiMap<PsiElement, String> conflicts) {
    if (scopes == null) return;
    for (final PsiElement scope : scopes) {
      if (scope instanceof PsiJavaPackage) return;
    }

    final Module targetModule = ModuleUtil.findModuleForFile(vFile, project);
    if (targetModule == null) return;
    final GlobalSearchScope resolveScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(targetModule);
    final HashSet<PsiElement> reported = new HashSet<PsiElement>();
    for (final PsiElement scope : scopes) {
      scope.accept(new JavaRecursiveElementVisitor() {
        @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          super.visitReferenceElement(reference);
          final PsiElement resolved = reference.resolve();
          if (resolved != null &&
              !reported.contains(resolved) &&
              !CommonRefactoringUtil.isAncestor(resolved, scopes) &&
              !PsiSearchScopeUtil.isInScope(resolveScope, resolved) && 
              !(resolved instanceof LightElement)) {
            final String scopeDescription = RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(reference), true);
            final LocalizeValue message = RefactoringLocalize.zeroReferencedIn1WillNotBeAccessibleInModule2(
                RefactoringUIUtil.getDescription(resolved, true),
                scopeDescription,
                CommonRefactoringUtil.htmlEmphasize(targetModule.getName())
              );
            conflicts.putValue(resolved, CommonRefactoringUtil.capitalize(message.get()));
            reported.add(resolved);
          }
        }
      });
    }

    boolean isInTestSources = ModuleRootManager.getInstance(targetModule).getFileIndex().isInTestSourceContent(vFile);
    NextUsage:
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element != null && PsiTreeUtil.getParentOfType(element, PsiImportStatement.class, false) == null) {

        for (PsiElement scope : scopes) {
          if (PsiTreeUtil.isAncestor(scope, element, false)) continue NextUsage;
        }

        final GlobalSearchScope resolveScope1 = element.getResolveScope();
        if (!resolveScope1.isSearchInModuleContent(targetModule, isInTestSources)) {
          final PsiFile usageFile = element.getContainingFile();
          PsiElement container;
          if (usageFile instanceof PsiJavaFile) {
            container = ConflictsUtil.getContainer(element);
          }
          else {
            container = usageFile;
          }
          final String scopeDescription = RefactoringUIUtil.getDescription(container, true);
          final VirtualFile usageVFile = usageFile.getVirtualFile();
          if (usageVFile != null) {
            Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(usageVFile);
            if (module != null) {
              final String message;
              final PsiElement referencedElement;
              if (usage instanceof MoveRenameUsageInfo) {
                referencedElement = ((MoveRenameUsageInfo)usage).getReferencedElement();
              }
              else {
                referencedElement = usage.getElement();
              }
              assert referencedElement != null : usage;
              String description = RefactoringUIUtil.getDescription(referencedElement, true);
              String emphasizedName = CommonRefactoringUtil.htmlEmphasize(module.getName());
              if (module == targetModule && isInTestSources) {
                message = RefactoringLocalize.zeroReferencedIn1WillNotBeAccessibleFromProductionOfModule2(description, scopeDescription, emphasizedName
                ).get();
              }
              else {
                message = RefactoringLocalize.zeroReferencedIn1WillNotBeAccessibleFromModule2(
                  description,
                  scopeDescription,
                  emphasizedName
                ).get();
              }
              conflicts.putValue(referencedElement, CommonRefactoringUtil.capitalize(message));
            }
          }
        }
      }
    }
  }
}
