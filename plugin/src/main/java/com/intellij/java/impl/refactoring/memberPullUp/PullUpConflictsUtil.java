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
package com.intellij.java.impl.refactoring.memberPullUp;

import com.intellij.java.impl.refactoring.util.RefactoringConflictsUtil;
import com.intellij.java.impl.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.java.impl.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.java.impl.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author dsl
 * @since 2002-06-17
 */
public class PullUpConflictsUtil {
    private PullUpConflictsUtil() {
    }

    @RequiredReadAction
    public static MultiMap<PsiElement, LocalizeValue> checkConflicts(
        MemberInfo[] infos,
        PsiClass subclass,
        @Nullable PsiClass superClass,
        @Nonnull PsiJavaPackage targetPackage,
        @Nonnull PsiDirectory targetDirectory,
        InterfaceContainmentVerifier interfaceContainmentVerifier
    ) {
        return checkConflicts(infos, subclass, superClass, targetPackage, targetDirectory, interfaceContainmentVerifier, true);
    }

    @RequiredReadAction
    public static MultiMap<PsiElement, LocalizeValue> checkConflicts(
        MemberInfo[] infos,
        @Nonnull final PsiClass subclass,
        @Nullable PsiClass superClass,
        @Nonnull PsiJavaPackage targetPackage,
        @Nonnull PsiDirectory targetDirectory,
        InterfaceContainmentVerifier interfaceContainmentVerifier,
        boolean movedMembers2Super
    ) {
        final Set<PsiMember> movedMembers = new HashSet<>();
        Set<PsiMethod> abstractMethods = new HashSet<>();
        boolean isInterfaceTarget;
        PsiElement targetRepresentativeElement;
        if (superClass != null) {
            isInterfaceTarget = superClass.isInterface();
            targetRepresentativeElement = superClass;
        }
        else {
            isInterfaceTarget = false;
            targetRepresentativeElement = targetDirectory;
        }
        for (MemberInfo info : infos) {
            PsiMember member = info.getMember();
            if (member instanceof PsiMethod method) {
                if (!info.isToAbstract() && !isInterfaceTarget) {
                    movedMembers.add(method);
                }
                else {
                    abstractMethods.add(method);
                }
            }
            else {
                movedMembers.add(member);
            }
        }
        final MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
        Set<PsiMethod> abstrMethods = new HashSet<>(abstractMethods);
        if (superClass != null) {
            for (PsiMethod method : subclass.getMethods()) {
                if (!movedMembers.contains(method) && !method.isPrivate() && method.findSuperMethods(superClass).length > 0) {
                    abstrMethods.add(method);
                }
            }
        }
        RefactoringConflictsUtil.analyzeAccessibilityConflicts(
            movedMembers,
            superClass,
            conflicts,
            VisibilityUtil.ESCALATE_VISIBILITY,
            targetRepresentativeElement,
            abstrMethods
        );
        if (superClass != null) {
            if (movedMembers2Super) {
                checkSuperclassMembers(superClass, infos, conflicts);
                if (isInterfaceTarget) {
                    checkInterfaceTarget(infos, conflicts);
                }
            }
            else {
                String qualifiedName = superClass.getQualifiedName();
                assert qualifiedName != null;
                if (superClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
                    if (!Comparing.strEqual(StringUtil.getPackageName(qualifiedName), targetPackage.getQualifiedName())) {
                        conflicts.putValue(
                            superClass,
                            LocalizeValue.localizeTODO(
                                RefactoringUIUtil.getDescription(superClass, true) +
                                    " won't be accessible from " + RefactoringUIUtil.getDescription(targetPackage, true)
                            )
                        );
                    }
                }
            }
        }
        // check if moved methods use other members in the classes between Subclass and Superclass
        List<PsiElement> checkModuleConflictsList = new ArrayList<>();
        for (PsiMember member : movedMembers) {
            if (member instanceof PsiMethod || member instanceof PsiClass && !(member instanceof PsiCompiledElement)) {
                ClassMemberReferencesVisitor visitor = movedMembers2Super
                    ? new ConflictingUsagesOfSubClassMembers(
                    member,
                    movedMembers,
                    abstractMethods,
                    subclass, superClass,
                    superClass != null ? null : targetPackage,
                    conflicts,
                    interfaceContainmentVerifier
                )
                    : new ConflictingUsagesOfSuperClassMembers(member, subclass, targetPackage, movedMembers, conflicts);
                member.accept(visitor);
            }
            checkModuleConflictsList.add(member);
        }
        for (PsiMethod method : abstractMethods) {
            checkModuleConflictsList.add(method.getParameterList());
            checkModuleConflictsList.add(method.getReturnTypeElement());
            checkModuleConflictsList.add(method.getTypeParameterList());
        }
        RefactoringConflictsUtil.analyzeModuleConflicts(
            subclass.getProject(),
            checkModuleConflictsList,
            new UsageInfo[0],
            targetRepresentativeElement,
            conflicts
        );
        String fqName = subclass.getQualifiedName();
        String packageName;
        if (fqName != null) {
            packageName = StringUtil.getPackageName(fqName);
        }
        else if (PsiTreeUtil.getParentOfType(subclass, PsiFile.class) instanceof PsiClassOwner classOwner) {
            packageName = classOwner.getPackageName();
        }
        else {
            packageName = null;
        }
        final boolean toDifferentPackage = !Comparing.strEqual(targetPackage.getQualifiedName(), packageName);
        for (final PsiMethod abstractMethod : abstractMethods) {
            abstractMethod.accept(new ClassMemberReferencesVisitor(subclass) {
                @Override
                protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
                    if (classMember != null && willBeMoved(classMember, movedMembers)) {
                        boolean isAccessible = false;
                        if (classMember.isPrivate()) {
                            isAccessible = true;
                        }
                        else if (classMember.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
                            toDifferentPackage) {
                            isAccessible = true;
                        }
                        if (isAccessible) {
                            String message = RefactoringUIUtil.getDescription(abstractMethod, false) +
                                " uses " +
                                RefactoringUIUtil.getDescription(classMember, true) +
                                " which won't be accessible from the subclass.";
                            conflicts.putValue(classMember, LocalizeValue.localizeTODO(CommonRefactoringUtil.capitalize(message)));
                        }
                    }
                }
            });
            if (abstractMethod.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && toDifferentPackage) {
                if (!isInterfaceTarget) {
                    String message = "Can't make " + RefactoringUIUtil.getDescription(abstractMethod, false) +
                        " abstract as it won't be accessible from the subclass.";
                    conflicts.putValue(abstractMethod, LocalizeValue.localizeTODO(message));
                }
            }
        }
        return conflicts;
    }

    private static void checkInterfaceTarget(MemberInfo[] infos, MultiMap<PsiElement, LocalizeValue> conflictsList) {
        for (MemberInfo info : infos) {
            PsiElement member = info.getMember();

            if (member instanceof PsiField || member instanceof PsiClass) {
                if (!((PsiModifierListOwner) member).hasModifierProperty(PsiModifier.STATIC)
                    && !(member instanceof PsiClass psiClass && psiClass.isInterface())) {
                    LocalizeValue message =
                        RefactoringLocalize.zeroIsNotStaticItCannotBeMovedToTheInterface(RefactoringUIUtil.getDescription(member, false));
                    conflictsList.putValue(member, message.capitalize());
                }
            }

            if (member instanceof PsiField field && field.getInitializer() == null) {
                LocalizeValue message = RefactoringLocalize.zeroIsNotInitializedInDeclarationSuchFieldsAreNotAllowedInInterfaces(
                    RefactoringUIUtil.getDescription(field, false)
                );
                conflictsList.putValue(field, message.capitalize());
            }
        }
    }

    @RequiredReadAction
    private static void checkSuperclassMembers(PsiClass superClass, MemberInfo[] infos, MultiMap<PsiElement, LocalizeValue> conflictsList) {
        for (MemberInfo info : infos) {
            PsiMember member = info.getMember();
            boolean isConflict = false;
            if (member instanceof PsiField field) {
                isConflict = superClass.findFieldByName(field.getName(), false) != null;
            }
            else if (member instanceof PsiMethod method) {
                PsiSubstitutor superSubstitutor =
                    TypeConversionUtil.getSuperClassSubstitutor(superClass, member.getContainingClass(), PsiSubstitutor.EMPTY);
                MethodSignature signature = method.getSignature(superSubstitutor);
                PsiMethod superClassMethod = MethodSignatureUtil.findMethodBySignature(superClass, signature, false);
                isConflict = superClassMethod != null;
            }

            if (isConflict) {
                LocalizeValue message = RefactoringLocalize.zeroAlreadyContainsA1(
                    RefactoringUIUtil.getDescription(superClass, false),
                    RefactoringUIUtil.getDescription(member, false)
                );
                conflictsList.putValue(superClass, message.capitalize());
            }

            if (member instanceof PsiMethod method) {
                PsiModifierList modifierList = method.getModifierList();
                if (!modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
                    for (PsiClass subClass : ClassInheritorsSearch.search(superClass)) {
                        if (method.getContainingClass() != subClass) {
                            MethodSignature signature = method.getSignature(
                                TypeConversionUtil.getSuperClassSubstitutor(superClass, subClass, PsiSubstitutor.EMPTY)
                            );
                            PsiMethod wouldBeOverridden = MethodSignatureUtil.findMethodBySignature(subClass, signature, false);
                            if (wouldBeOverridden != null && VisibilityUtil.compare(
                                VisibilityUtil.getVisibilityModifier(wouldBeOverridden.getModifierList()),
                                VisibilityUtil.getVisibilityModifier(modifierList)
                            ) > 0) {
                                conflictsList.putValue(
                                    wouldBeOverridden,
                                    LocalizeValue.localizeTODO(CommonRefactoringUtil.capitalize(
                                        RefactoringUIUtil.getDescription(method, true) +
                                            " in super class would clash with local method from " +
                                            RefactoringUIUtil.getDescription(subClass, true)
                                    ))
                                );
                            }
                        }
                    }
                }
            }
        }

    }

    private static boolean willBeMoved(PsiElement element, Set<PsiMember> movedMembers) {
        PsiElement parent = element;
        while (parent != null) {
            if (movedMembers.contains(parent)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private static class ConflictingUsagesOfSuperClassMembers extends ClassMemberReferencesVisitor {
        private PsiMember myMember;
        private PsiClass mySubClass;
        private PsiJavaPackage myTargetPackage;
        private Set<PsiMember> myMovedMembers;
        private MultiMap<PsiElement, LocalizeValue> myConflicts;

        public ConflictingUsagesOfSuperClassMembers(
            PsiMember member, PsiClass aClass,
            PsiJavaPackage targetPackage,
            Set<PsiMember> movedMembers,
            MultiMap<PsiElement, LocalizeValue> conflicts
        ) {
            super(aClass);
            myMember = member;
            mySubClass = aClass;
            myTargetPackage = targetPackage;
            myMovedMembers = movedMembers;
            myConflicts = conflicts;
        }

        @Override
        protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
            if (classMember != null && !willBeMoved(classMember, myMovedMembers)) {
                PsiClass containingClass = classMember.getContainingClass();
                if (containingClass != null) {
                    if (!PsiUtil.isAccessibleFromPackage(classMember, myTargetPackage)) {
                        if (classMember.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
                            myConflicts.putValue(
                                myMember,
                                LocalizeValue.localizeTODO(RefactoringUIUtil.getDescription(classMember, true) + " won't be accessible")
                            );
                        }
                        else if (classMember.isProtected() && !mySubClass.isInheritor(containingClass, true)) {
                            myConflicts.putValue(
                                myMember,
                                LocalizeValue.localizeTODO(RefactoringUIUtil.getDescription(classMember, true) + " won't be accessible")
                            );
                        }
                    }
                }
            }
        }
    }

    private static class ConflictingUsagesOfSubClassMembers extends ClassMemberReferencesVisitor {
        private final PsiElement myScope;
        private final Set<PsiMember> myMovedMembers;
        private final Set<PsiMethod> myAbstractMethods;
        private final PsiClass mySubclass;
        private final PsiClass mySuperClass;
        private final PsiJavaPackage myTargetPackage;
        private final MultiMap<PsiElement, LocalizeValue> myConflictsList;
        private final InterfaceContainmentVerifier myInterfaceContainmentVerifier;

        ConflictingUsagesOfSubClassMembers(
            PsiElement scope,
            Set<PsiMember> movedMembers,
            Set<PsiMethod> abstractMethods,
            PsiClass subclass,
            PsiClass superClass,
            PsiJavaPackage targetPackage,
            MultiMap<PsiElement, LocalizeValue> conflictsList,
            InterfaceContainmentVerifier interfaceContainmentVerifier
        ) {
            super(subclass);
            myScope = scope;
            myMovedMembers = movedMembers;
            myAbstractMethods = abstractMethods;
            mySubclass = subclass;
            mySuperClass = superClass;
            myTargetPackage = targetPackage;
            myConflictsList = conflictsList;
            myInterfaceContainmentVerifier = interfaceContainmentVerifier;
        }

        @Override
        protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
            if (classMember != null
                && RefactoringHierarchyUtil.isMemberBetween(mySuperClass, mySubclass, classMember)) {
                if (classMember.isStatic() && !willBeMoved(classMember, myMovedMembers)) {
                    boolean isAccessible;
                    if (mySuperClass != null) {
                        isAccessible = PsiUtil.isAccessible(classMember, mySuperClass, null);
                    }
                    else if (myTargetPackage != null) {
                        isAccessible = PsiUtil.isAccessibleFromPackage(classMember, myTargetPackage);
                    }
                    else {
                        isAccessible = classMember.isPublic();
                    }
                    if (!isAccessible) {
                        LocalizeValue message = RefactoringLocalize.zeroUses1WhichIsNotAccessibleFromTheSuperclass(
                            RefactoringUIUtil.getDescription(myScope, false),
                            RefactoringUIUtil.getDescription(classMember, true)
                        );
                        myConflictsList.putValue(classMember, message.capitalize());
                    }
                    return;
                }
                if (!myAbstractMethods.contains(classMember) && !willBeMoved(classMember, myMovedMembers)) {
                    if (!existsInSuperClass(classMember)) {
                        LocalizeValue message = RefactoringLocalize.zeroUses1WhichIsNotMovedToTheSuperclass(
                            RefactoringUIUtil.getDescription(myScope, false),
                            RefactoringUIUtil.getDescription(classMember, true)
                        );
                        myConflictsList.putValue(classMember, message.capitalize());
                    }
                }
            }
        }


        private boolean existsInSuperClass(PsiElement classMember) {
            if (!(classMember instanceof PsiMethod method)) {
                return false;
            }
            if (myInterfaceContainmentVerifier.checkedInterfacesContain(method)) {
                return true;
            }
            if (mySuperClass == null) {
                return false;
            }
            PsiMethod methodBySignature = mySuperClass.findMethodBySignature(method, true);
            return methodBySignature != null;
        }
    }
}
