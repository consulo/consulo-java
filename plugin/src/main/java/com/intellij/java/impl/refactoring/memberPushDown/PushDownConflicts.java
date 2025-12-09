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
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.progress.ProgressManager;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.localize.LocalizeValue;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

public class PushDownConflicts {
    private final PsiClass myClass;
    private final Set<PsiMember> myMovedMembers;
    private final Set<PsiMethod> myAbstractMembers;
    private final MultiMap<PsiElement, LocalizeValue> myConflicts;

    public PushDownConflicts(PsiClass aClass, MemberInfo[] memberInfos) {
        myClass = aClass;

        myMovedMembers = new HashSet<>();
        myAbstractMembers = new HashSet<>();
        for (MemberInfo memberInfo : memberInfos) {
            PsiMember member = memberInfo.getMember();
            if (memberInfo.isChecked() && (!(memberInfo.getMember() instanceof PsiClass) || memberInfo.getOverrides() == null)) {
                myMovedMembers.add(member);
                if (memberInfo.isToAbstract()) {
                    myAbstractMembers.add((PsiMethod) member);
                }
            }
        }

        myConflicts = new MultiMap<>();
    }

    public boolean isAnyConflicts() {
        return !myConflicts.isEmpty();
    }

    public MultiMap<PsiElement, LocalizeValue> getConflicts() {
        return myConflicts;
    }

    @RequiredReadAction
    public void checkSourceClassConflicts() {
        PsiElement[] children = myClass.getChildren();
        for (PsiElement child : children) {
            if (child instanceof PsiMember && !myMovedMembers.contains(child)) {
                child.accept(new UsedMovedMembersConflictsCollector(child));
            }
        }
    }

    @RequiredReadAction
    public boolean checkTargetClassConflicts(PsiClass targetClass, boolean checkStatic, PsiElement context) {
        if (targetClass != null) {
            for (PsiMember movedMember : myMovedMembers) {
                checkMemberPlacementInTargetClassConflict(targetClass, movedMember);
                movedMember.accept(new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
                        super.visitMethodCallExpression(expression);
                        if (expression.getMethodExpression().getQualifierExpression() instanceof PsiSuperExpression) {
                            PsiMethod resolvedMethod = expression.resolveMethod();
                            if (resolvedMethod != null) {
                                PsiClass resolvedClass = resolvedMethod.getContainingClass();
                                if (resolvedClass != null && myClass.isInheritor(resolvedClass, true)) {
                                    PsiMethod methodBySignature = myClass.findMethodBySignature(resolvedMethod, false);
                                    if (methodBySignature != null && !myMovedMembers.contains(methodBySignature)) {
                                        myConflicts.putValue(
                                            expression,
                                            LocalizeValue.localizeTODO("Super method call will resolve to another method")
                                        );
                                    }
                                }
                            }
                        }
                    }
                });
            }
        }
        @RequiredWriteAction
        Runnable searchConflictsRunnable = () -> {
            Members:
            for (PsiMember member : myMovedMembers) {
                for (PsiReference ref : ReferencesSearch.search(member, member.getResolveScope(), false)) {
                    if (ref.getElement() instanceof PsiReferenceExpression refExpr) {
                        PsiExpression qualifier = refExpr.getQualifierExpression();
                        if (qualifier != null) {
                            PsiClass aClass = null;
                            if (qualifier.getType() instanceof PsiClassType classType) {
                                aClass = classType.resolve();
                            }
                            else if (!checkStatic) {
                                continue;
                            }
                            else if (qualifier instanceof PsiReferenceExpression qRefExpr
                                && qRefExpr.resolve() instanceof PsiClass psiClass) {
                                aClass = psiClass;
                            }

                            if (!InheritanceUtil.isInheritorOrSelf(aClass, targetClass, true)) {
                                myConflicts.putValue(
                                    refExpr,
                                    RefactoringLocalize.pushedMembersWillNotBeVisibleFromCertainCallSites()
                                );
                                break Members;
                            }
                        }
                    }
                }
            }
            RefactoringConflictsUtil.analyzeAccessibilityConflicts(
                myMovedMembers,
                targetClass,
                myConflicts,
                null,
                context,
                myAbstractMembers
            );
        };
        return !ProgressManager.getInstance().runProcessWithProgressSynchronously(
            searchConflictsRunnable,
            RefactoringLocalize.detectingPossibleConflicts(),
            false,
            context.getProject()
        );
    }

    @RequiredReadAction
    public void checkMemberPlacementInTargetClassConflict(PsiClass targetClass, PsiMember movedMember) {
        if (movedMember instanceof PsiField movedField) {
            String name = movedField.getName();
            PsiField field = targetClass.findFieldByName(name, false);
            if (field != null) {
                LocalizeValue message = RefactoringLocalize.zeroAlreadyContainsField1(
                    RefactoringUIUtil.getDescription(targetClass, false),
                    CommonRefactoringUtil.htmlEmphasize(name)
                );
                myConflicts.putValue(field, message.capitalize());
            }
        }
        else if (movedMember instanceof PsiMethod movedMethod) {
            if (!movedMethod.isAbstract()) {
                PsiMethod overrider = targetClass.findMethodBySignature(movedMethod, false);
                if (overrider != null) {
                    LocalizeValue message = RefactoringLocalize.zeroIsAlreadyOverriddenIn1(
                        RefactoringUIUtil.getDescription(movedMethod, true),
                        RefactoringUIUtil.getDescription(targetClass, false)
                    );
                    myConflicts.putValue(overrider, message.capitalize());
                }
            }
        }
        else if (movedMember instanceof PsiClass movedClass) {
            String name = movedClass.getName();
            PsiClass[] allInnerClasses = targetClass.getAllInnerClasses();
            for (PsiClass innerClass : allInnerClasses) {
                if (innerClass.equals(movedClass)) {
                    continue;
                }

                if (name.equals(innerClass.getName())) {
                    LocalizeValue message = RefactoringLocalize.zeroAlreadyContainsInnerClassNamed1(
                        RefactoringUIUtil.getDescription(targetClass, false),
                        CommonRefactoringUtil.htmlEmphasize(name)
                    );
                    myConflicts.putValue(innerClass, message);
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

        @Override
        protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
            if (myMovedMembers.contains(classMember) && !myAbstractMembers.contains(classMember)) {
                LocalizeValue message = RefactoringLocalize.zeroUses1WhichIsPushedDown(
                    RefactoringUIUtil.getDescription(mySource, false),
                    RefactoringUIUtil.getDescription(classMember, false)
                );
                myConflicts.putValue(mySource, message.capitalize());
            }
        }
    }
}
