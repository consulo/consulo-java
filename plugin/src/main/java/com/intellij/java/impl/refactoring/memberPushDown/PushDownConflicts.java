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
package com.intellij.java.impl.refactoring.memberPushDown;

import com.intellij.java.impl.refactoring.util.RefactoringConflictsUtil;
import com.intellij.java.impl.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.application.progress.ProgressManager;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.localize.LocalizeValue;
import consulo.util.collection.MultiMap;

import java.util.HashSet;
import java.util.Set;

public class PushDownConflicts {
  private final PsiClass myClass;
  private final Set<PsiMember> myMovedMembers;
  private final Set<PsiMethod> myAbstractMembers;
  private final MultiMap<PsiElement, String> myConflicts;


  public PushDownConflicts(PsiClass aClass, MemberInfo[] memberInfos) {
    myClass = aClass;

    myMovedMembers = new HashSet<PsiMember>();
    myAbstractMembers = new HashSet<PsiMethod>();
    for (MemberInfo memberInfo : memberInfos) {
      final PsiMember member = memberInfo.getMember();
      if (memberInfo.isChecked() && (!(memberInfo.getMember() instanceof PsiClass) || memberInfo.getOverrides() == null)) {
        myMovedMembers.add(member);
        if (memberInfo.isToAbstract()) {
          myAbstractMembers.add((PsiMethod)member);
        }
      }
    }

    myConflicts = new MultiMap<PsiElement, String>();
  }

  public boolean isAnyConflicts() {
    return !myConflicts.isEmpty();
  }

  public MultiMap<PsiElement, String> getConflicts() {
    return myConflicts;
  }

  public void checkSourceClassConflicts() {
    final PsiElement[] children = myClass.getChildren();
    for (PsiElement child : children) {
      if (child instanceof PsiMember && !myMovedMembers.contains(child)) {
        child.accept(new UsedMovedMembersConflictsCollector(child));
      }
    }
  }

  public boolean checkTargetClassConflicts(final PsiClass targetClass, final boolean checkStatic, final PsiElement context) {
    if (targetClass != null) {
      for (final PsiMember movedMember : myMovedMembers) {
        checkMemberPlacementInTargetClassConflict(targetClass, movedMember);
        movedMember.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (expression.getMethodExpression().getQualifierExpression() instanceof PsiSuperExpression) {
              final PsiMethod resolvedMethod = expression.resolveMethod();
              if (resolvedMethod != null) {
                final PsiClass resolvedClass = resolvedMethod.getContainingClass();
                if (resolvedClass != null) {
                  if (myClass.isInheritor(resolvedClass, true)) {
                    final PsiMethod methodBySignature = myClass.findMethodBySignature(resolvedMethod, false);
                    if (methodBySignature != null && !myMovedMembers.contains(methodBySignature)) {
                      myConflicts.putValue(expression, "Super method call will resolve to another method");
                    }
                  }
                }
              }
            }
          }
        });
      }
    }
    Runnable searchConflictsRunnable = new Runnable() {
      public void run() {
        Members:
        for (PsiMember member : myMovedMembers) {
          for (PsiReference ref : ReferencesSearch.search(member, member.getResolveScope(), false)) {
            final PsiElement element = ref.getElement();
            if (element instanceof PsiReferenceExpression) {
              final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
              final PsiExpression qualifier = referenceExpression.getQualifierExpression();
              if (qualifier != null) {
                final PsiType qualifierType = qualifier.getType();
                PsiClass aClass = null;
                if (qualifierType instanceof PsiClassType) {
                  aClass = ((PsiClassType)qualifierType).resolve();
                }
                else {
                  if (!checkStatic) continue;
                  if (qualifier instanceof PsiReferenceExpression) {
                    final PsiElement resolved = ((PsiReferenceExpression)qualifier).resolve();
                    if (resolved instanceof PsiClass) {
                      aClass = (PsiClass)resolved;
                    }
                  }
                }

                if (!InheritanceUtil.isInheritorOrSelf(aClass, targetClass, true)) {
                  myConflicts.putValue(referenceExpression, RefactoringLocalize.pushedMembersWillNotBeVisibleFromCertainCallSites().get());
                  break Members;
                }
              }
            }
          }
        }
        RefactoringConflictsUtil.analyzeAccessibilityConflicts(myMovedMembers, targetClass, myConflicts, null, context, myAbstractMembers);
      }
    };
    return !ProgressManager.getInstance().runProcessWithProgressSynchronously(
      searchConflictsRunnable,
      RefactoringLocalize.detectingPossibleConflicts().get(),
      false,
      context.getProject()
    );
  }

  public void checkMemberPlacementInTargetClassConflict(final PsiClass targetClass, final PsiMember movedMember) {
    if (movedMember instanceof PsiField) {
      String name = movedMember.getName();
      final PsiField field = targetClass.findFieldByName(name, false);
      if (field != null) {
        LocalizeValue message = RefactoringLocalize.zeroAlreadyContainsField1(
          RefactoringUIUtil.getDescription(targetClass, false),
          CommonRefactoringUtil.htmlEmphasize(name)
        );
        myConflicts.putValue(field, CommonRefactoringUtil.capitalize(message.get()));
      }
    }
    else if (movedMember instanceof PsiMethod) {
      final PsiModifierList modifierList = movedMember.getModifierList();
      assert modifierList != null;
      if (!modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
        PsiMethod method = (PsiMethod)movedMember;
        final PsiMethod overrider = targetClass.findMethodBySignature(method, false);
        if (overrider != null) {
          LocalizeValue message = RefactoringLocalize.zeroIsAlreadyOverriddenIn1(
            RefactoringUIUtil.getDescription(method, true),
            RefactoringUIUtil.getDescription(targetClass, false)
          );
          myConflicts.putValue(overrider, CommonRefactoringUtil.capitalize(message.get()));
        }
      }
    }
    else if (movedMember instanceof PsiClass) {
      PsiClass aClass = (PsiClass)movedMember;
      final String name = aClass.getName();
      final PsiClass[] allInnerClasses = targetClass.getAllInnerClasses();
      for (PsiClass innerClass : allInnerClasses) {
        if (innerClass.equals(movedMember)) continue;

        if (name.equals(innerClass.getName())) {
          LocalizeValue message = RefactoringLocalize.zeroAlreadyContainsInnerClassNamed1(
            RefactoringUIUtil.getDescription(targetClass, false),
            CommonRefactoringUtil.htmlEmphasize(name)
          );
          myConflicts.putValue(innerClass, message.get());
        }
      }
    }
  }

  private class UsedMovedMembersConflictsCollector extends ClassMemberReferencesVisitor {
    private final PsiElement mySource;

    public UsedMovedMembersConflictsCollector(PsiElement source) {
      super(myClass);
      mySource = source;
    }

    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if(myMovedMembers.contains(classMember) && !myAbstractMembers.contains(classMember)) {
        LocalizeValue message = RefactoringLocalize.zeroUses1WhichIsPushedDown(
          RefactoringUIUtil.getDescription(mySource, false),
          RefactoringUIUtil.getDescription(classMember, false)
        );
        myConflicts.putValue(mySource, CommonRefactoringUtil.capitalize(message.get()));
      }
    }
  }
}
