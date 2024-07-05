/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.move.moveMembers;

import com.intellij.java.impl.refactoring.util.*;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.impl.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.highlight.ReadWriteAccessDetector;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class MoveJavaMemberHandler implements MoveMemberHandler {
  @Override
  @Nullable
  public MoveMembersProcessor.MoveMembersUsageInfo getUsage(@Nonnull PsiMember member, @Nonnull PsiReference psiReference,
                                                            @Nonnull Set<PsiMember> membersToMove, @Nonnull PsiClass targetClass) {
    PsiElement ref = psiReference.getElement();
    if (ref instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression) ref;
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (RefactoringHierarchyUtil.willBeInTargetClass(refExpr, membersToMove, targetClass, true)) {
        // both member and the reference to it will be in target class
        if (!RefactoringUtil.isInMovedElement(refExpr, membersToMove)) {
          if (qualifier != null) {
            return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, null, qualifier, psiReference);  // remove qualifier
          }
        } else {
          if (qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression) qualifier).isReferenceTo(member.getContainingClass
              ())) {
            return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, null, qualifier, psiReference);  // change qualifier
          }
        }
      } else {
        // member in target class, the reference will be outside target class
        if (qualifier == null) {
          return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, targetClass, refExpr, psiReference); // add qualifier
        } else {
          return new MoveMembersProcessor.MoveMembersUsageInfo(member, refExpr, targetClass, qualifier, psiReference); // change qualifier
        }
      }
    }
    return null;
  }

  @Override
  public void checkConflictsOnUsage(@Nonnull MoveMembersProcessor.MoveMembersUsageInfo usageInfo, @Nullable String newVisibility,
                                    @Nullable PsiModifierList modifierListCopy, @Nonnull PsiClass targetClass, @Nonnull Set<PsiMember> membersToMove,
                                    @Nonnull MultiMap<PsiElement, String> conflicts) {
    final PsiElement element = usageInfo.getElement();
    if (element == null) {
      return;
    }

    final PsiMember member = usageInfo.member;
    if (element instanceof PsiReferenceExpression) {
      PsiExpression qualifier = ((PsiReferenceExpression) element).getQualifierExpression();
      PsiClass accessObjectClass = null;
      if (qualifier != null) {
        accessObjectClass = (PsiClass) PsiUtil.getAccessObjectClass(qualifier).getElement();
      }

      if (!JavaResolveUtil.isAccessible(member, targetClass, modifierListCopy, element, accessObjectClass, null)) {
        String visibility = newVisibility != null ? newVisibility : VisibilityUtil.getVisibilityStringToDisplay(member);
        LocalizeValue message = RefactoringLocalize.zeroWith1VisibilityIsNotAccessibleFrom2(
          RefactoringUIUtil.getDescription(member, false),
          visibility,
          RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true)
        );
        conflicts.putValue(member, CommonRefactoringUtil.capitalize(message.get()));
      }
    }

    if (member instanceof PsiField && targetClass.isInterface()) {
      ReadWriteAccessDetector accessDetector = ReadWriteAccessDetector.findDetector(member);
      if (accessDetector != null) {
        ReadWriteAccessDetector.Access access = accessDetector.getExpressionAccess(element);
        if (access != ReadWriteAccessDetector.Access.Read) {
          String message = RefactoringUIUtil.getDescription(member, true) + " has write access but is moved to an interface";
          conflicts.putValue(element, CommonRefactoringUtil.capitalize(message));
        }
      }
    }

    final PsiReference reference = usageInfo.getReference();
    if (reference != null) {
      RefactoringConflictsUtil.checkAccessibilityConflicts(reference, member, modifierListCopy, targetClass, membersToMove, conflicts);
    }
  }

  @Override
  public void checkConflictsOnMember(@Nonnull PsiMember member, @Nullable String newVisibility, @Nullable PsiModifierList modifierListCopy,
                                     @Nonnull PsiClass targetClass, @Nonnull Set<PsiMember> membersToMove, @Nonnull MultiMap<PsiElement, String> conflicts) {
    if (member instanceof PsiMethod && hasMethod(targetClass, (PsiMethod) member) || member instanceof PsiField && hasField(targetClass,
        (PsiField) member)) {
      LocalizeValue message =
        RefactoringLocalize.zeroAlreadyExistsInTheTargetClass(RefactoringUIUtil.getDescription(member, false));
      conflicts.putValue(member, CommonRefactoringUtil.capitalize(message.get()));
    }

    RefactoringConflictsUtil.checkUsedElements(member, member, membersToMove, null, targetClass, targetClass, conflicts);
  }

  protected static boolean hasMethod(PsiClass targetClass, PsiMethod method) {
    PsiMethod[] targetClassMethods = targetClass.getMethods();
    for (PsiMethod candidate : targetClassMethods) {
      if (candidate != method && MethodSignatureUtil.areSignaturesEqual(method.getSignature(PsiSubstitutor.EMPTY),
          candidate.getSignature(PsiSubstitutor.EMPTY))) {
        return true;
      }
    }
    return false;
  }

  protected static boolean hasField(PsiClass targetClass, PsiField field) {
    String fieldName = field.getName();
    PsiField[] targetClassFields = targetClass.getFields();
    for (PsiField candidate : targetClassFields) {
      if (candidate != field && fieldName.equals(candidate.getName())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean changeExternalUsage(@Nonnull MoveMembersOptions options, @Nonnull MoveMembersProcessor.MoveMembersUsageInfo usage) {
    final PsiElement element = usage.getElement();
    if (element == null || !element.isValid()) {
      return true;
    }

    if (usage.reference instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression) usage.reference;
      PsiExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier != null) {
        if (usage.qualifierClass != null && PsiTreeUtil.getParentOfType(refExpr, PsiSwitchLabelStatement.class) == null) {
          changeQualifier(refExpr, usage.qualifierClass, usage.member);
        } else {
          final PsiReferenceParameterList parameterList = refExpr.getParameterList();
          if (parameterList != null && parameterList.getTypeArguments().length == 0) {
            refExpr.setQualifierExpression(null);
          } else {
            final Project project = element.getProject();
            final PsiClass targetClass = JavaPsiFacade.getInstance(project).findClass(options.getTargetClassName(),
                GlobalSearchScope.projectScope(project));
            if (targetClass != null) {
              changeQualifier(refExpr, targetClass, usage.member);
            }
          }
        }
      } else { // no qualifier
        if (usage.qualifierClass != null && PsiTreeUtil.getParentOfType(refExpr, PsiSwitchLabelStatement.class) == null) {
          changeQualifier(refExpr, usage.qualifierClass, usage.member);
        }
      }
      return true;
    }
    return false;
  }

  protected static void changeQualifier(PsiReferenceExpression refExpr, PsiClass aClass, PsiMember member) throws IncorrectOperationException {
    if (RefactoringUtil.hasOnDemandStaticImport(refExpr, aClass)) {
      refExpr.setQualifierExpression(null);
    } else if (!RefactoringUtil.hasStaticImportOn(refExpr, member)) {
      PsiElementFactory factory = JavaPsiFacade.getInstance(refExpr.getProject()).getElementFactory();
      refExpr.setQualifierExpression(factory.createReferenceExpression(aClass));
    }
  }

  @Override
  @Nonnull
  public PsiMember doMove(@Nonnull MoveMembersOptions options, @Nonnull PsiMember member, PsiElement anchor, @Nonnull PsiClass targetClass) {
    if (member instanceof PsiVariable) {
      ((PsiVariable) member).normalizeDeclaration();
    }

    ChangeContextUtil.encodeContextInfo(member, true);

    final PsiMember memberCopy;
    if (options.makeEnumConstant() &&
        member instanceof PsiVariable &&
        EnumConstantsUtil.isSuitableForEnumConstant(((PsiVariable) member).getType(), targetClass)) {
      memberCopy = EnumConstantsUtil.createEnumConstant(targetClass, member.getName(), ((PsiVariable) member).getInitializer());
    } else {
      memberCopy = (PsiMember) member.copy();
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass != null && containingClass.isInterface() && !targetClass.isInterface()) {
        // might need to make modifiers explicit, see IDEADEV-11416
        final PsiModifierList list = memberCopy.getModifierList();
        assert list != null;
        list.setModifierProperty(PsiModifier.STATIC, member.hasModifierProperty(PsiModifier.STATIC));
        list.setModifierProperty(PsiModifier.FINAL, member.hasModifierProperty(PsiModifier.FINAL));
        VisibilityUtil.setVisibility(list, VisibilityUtil.getVisibilityModifier(member.getModifierList()));
      }
    }
    member.delete();
    return anchor != null ? (PsiMember) targetClass.addAfter(memberCopy, anchor) : (PsiMember) targetClass.add(memberCopy);
  }

  @Override
  public void decodeContextInfo(@Nonnull PsiElement scope) {
    ChangeContextUtil.decodeContextInfo(scope, null, null);
  }

  @Override
  @Nullable
  public PsiElement getAnchor(@Nonnull final PsiMember member, @Nonnull final PsiClass targetClass, final Set<PsiMember> membersToMove) {
    if (member instanceof PsiField && member.hasModifierProperty(PsiModifier.STATIC)) {
      final List<PsiField> afterFields = new ArrayList<PsiField>();
      final PsiExpression psiExpression = ((PsiField) member).getInitializer();
      if (psiExpression != null) {
        psiExpression.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitReferenceExpression(final PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement psiElement = expression.resolve();
            if (psiElement instanceof PsiField) {
              final PsiField psiField = (PsiField) psiElement;
              if ((psiField.getContainingClass() == targetClass || membersToMove.contains(psiField)) && !afterFields.contains(psiField)) {
                afterFields.add(psiField);
              }
            }
          }
        });
      }

      if (!afterFields.isEmpty()) {
        Collections.sort(afterFields, new Comparator<PsiField>() {
          @Override
          public int compare(final PsiField o1, final PsiField o2) {
            return -PsiUtilCore.compareElementsByPosition(o1, o2);
          }
        });
        return afterFields.get(0);
      }

      final List<PsiField> beforeFields = new ArrayList<PsiField>();
      for (PsiReference psiReference : ReferencesSearch.search(member, new LocalSearchScope(targetClass))) {
        final PsiField fieldWithReference = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiField.class);
        if (fieldWithReference != null && !afterFields.contains(fieldWithReference) && fieldWithReference.getContainingClass() == targetClass) {
          beforeFields.add(fieldWithReference);
        }
      }
      Collections.sort(beforeFields, PsiUtil.BY_POSITION);
      if (!beforeFields.isEmpty()) {
        return beforeFields.get(0).getPrevSibling();
      }
    }
    return null;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
