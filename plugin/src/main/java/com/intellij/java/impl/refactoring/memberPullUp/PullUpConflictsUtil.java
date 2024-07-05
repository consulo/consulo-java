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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 17.06.2002
 * Time: 15:40:16
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
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

public class PullUpConflictsUtil {
  private PullUpConflictsUtil() {}

  public static MultiMap<PsiElement, String> checkConflicts(final MemberInfo[] infos,
                                        PsiClass subclass,
                                        @Nullable PsiClass superClass,
                                        @Nonnull PsiJavaPackage targetPackage,
                                        @Nonnull PsiDirectory targetDirectory,
                                        final InterfaceContainmentVerifier interfaceContainmentVerifier) {
    return checkConflicts(infos, subclass, superClass, targetPackage, targetDirectory, interfaceContainmentVerifier, true);
  }

  public static MultiMap<PsiElement, String> checkConflicts(final MemberInfo[] infos,
                                                            @Nonnull final PsiClass subclass,
                                                            @Nullable PsiClass superClass,
                                                            @Nonnull final PsiJavaPackage targetPackage,
                                                            @Nonnull PsiDirectory targetDirectory,
                                                            final InterfaceContainmentVerifier interfaceContainmentVerifier,
                                                            boolean movedMembers2Super) {
    final Set<PsiMember> movedMembers = new HashSet<PsiMember>();
    final Set<PsiMethod> abstractMethods = new HashSet<PsiMethod>();
    final boolean isInterfaceTarget;
    final PsiElement targetRepresentativeElement;
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
      if (member instanceof PsiMethod) {
        if (!info.isToAbstract() && !isInterfaceTarget) {
          movedMembers.add(member);
        }
        else {
          abstractMethods.add((PsiMethod)member);
        }
      }
      else {
        movedMembers.add(member);
      }
    }
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    final Set<PsiMethod> abstrMethods = new HashSet<PsiMethod>(abstractMethods);
    if (superClass != null) {
      for (PsiMethod method : subclass.getMethods()) {
        if (!movedMembers.contains(method) && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
          if (method.findSuperMethods(superClass).length > 0) {
            abstrMethods.add(method);
          }
        }
      }
    }
    RefactoringConflictsUtil.analyzeAccessibilityConflicts(movedMembers, superClass, conflicts, VisibilityUtil.ESCALATE_VISIBILITY, targetRepresentativeElement, abstrMethods);
    if (superClass != null) {
      if (movedMembers2Super) {
        checkSuperclassMembers(superClass, infos, conflicts);
        if (isInterfaceTarget) {
          checkInterfaceTarget(infos, conflicts);
        }
      } else {
        final String qualifiedName = superClass.getQualifiedName();
        assert qualifiedName != null;
        if (superClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
          if (!Comparing.strEqual(StringUtil.getPackageName(qualifiedName), targetPackage.getQualifiedName())) {
            conflicts.putValue(superClass, RefactoringUIUtil.getDescription(superClass, true) + " won't be accessible from " +RefactoringUIUtil.getDescription(targetPackage, true));
          }
        }
      }
    }
    // check if moved methods use other members in the classes between Subclass and Superclass
    List<PsiElement> checkModuleConflictsList = new ArrayList<PsiElement>();
    for (PsiMember member : movedMembers) {
      if (member instanceof PsiMethod || member instanceof PsiClass && !(member instanceof PsiCompiledElement)) {
        ClassMemberReferencesVisitor visitor =
          movedMembers2Super? new ConflictingUsagesOfSubClassMembers(member, movedMembers, abstractMethods, subclass, superClass,
                                                 superClass != null ? null : targetPackage, conflicts,
                                                 interfaceContainmentVerifier)
                            : new ConflictingUsagesOfSuperClassMemebers(member, subclass, targetPackage, movedMembers, conflicts);
        member.accept(visitor);
      }
      checkModuleConflictsList.add(member);
    }
    for (final PsiMethod method : abstractMethods) {
      checkModuleConflictsList.add(method.getParameterList());
      checkModuleConflictsList.add(method.getReturnTypeElement());
      checkModuleConflictsList.add(method.getTypeParameterList());
    }
    RefactoringConflictsUtil.analyzeModuleConflicts(subclass.getProject(), checkModuleConflictsList,
                                           new UsageInfo[0], targetRepresentativeElement, conflicts);
    final String fqName = subclass.getQualifiedName();
    final String packageName;
    if (fqName != null) {
      packageName = StringUtil.getPackageName(fqName);
    } else {
      final PsiFile psiFile = PsiTreeUtil.getParentOfType(subclass, PsiFile.class);
      if (psiFile instanceof PsiClassOwner) {
        packageName = ((PsiClassOwner)psiFile).getPackageName();
      } else {
        packageName = null;
      }
    }
    final boolean toDifferentPackage = !Comparing.strEqual(targetPackage.getQualifiedName(), packageName);
    for (final PsiMethod abstractMethod : abstractMethods) {
      abstractMethod.accept(new ClassMemberReferencesVisitor(subclass) {
        @Override
        protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
          if (classMember != null && willBeMoved(classMember, movedMembers)) {
            boolean isAccessible = false;
            if (classMember.hasModifierProperty(PsiModifier.PRIVATE)) {
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
              conflicts.putValue(classMember, CommonRefactoringUtil.capitalize(message));
            }
          }
        }
      });
      if (abstractMethod.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && toDifferentPackage) {
        if (!isInterfaceTarget) {
          String message = "Can't make " + RefactoringUIUtil.getDescription(abstractMethod, false) +
                           " abstract as it won't be accessible from the subclass.";
          conflicts.putValue(abstractMethod, CommonRefactoringUtil.capitalize(message));
        }
      }
    }
    return conflicts;
  }

  private static void checkInterfaceTarget(MemberInfo[] infos, MultiMap<PsiElement, String> conflictsList) {
    for (MemberInfo info : infos) {
      PsiElement member = info.getMember();

      if (member instanceof PsiField || member instanceof PsiClass) {

        if (!((PsiModifierListOwner)member).hasModifierProperty(PsiModifier.STATIC)
            && !(member instanceof PsiClass && ((PsiClass)member).isInterface())) {
          LocalizeValue message =
            RefactoringLocalize.zeroIsNotStaticItCannotBeMovedToTheInterface(RefactoringUIUtil.getDescription(member, false));
          conflictsList.putValue(member, CommonRefactoringUtil.capitalize(message.get()));
        }
      }

      if (member instanceof PsiField && ((PsiField)member).getInitializer() == null) {
        LocalizeValue message = RefactoringLocalize.zeroIsNotInitializedInDeclarationSuchFieldsAreNotAllowedInInterfaces(
          RefactoringUIUtil.getDescription(member, false)
        );
        conflictsList.putValue(member, CommonRefactoringUtil.capitalize(message.get()));
      }
    }
  }

  private static void checkSuperclassMembers(PsiClass superClass,
                                             MemberInfo[] infos,
                                             MultiMap<PsiElement, String> conflictsList) {
    for (MemberInfo info : infos) {
      PsiMember member = info.getMember();
      boolean isConflict = false;
      if (member instanceof PsiField) {
        String name = member.getName();

        isConflict = superClass.findFieldByName(name, false) != null;
      }
      else if (member instanceof PsiMethod) {
        PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, member.getContainingClass(), PsiSubstitutor.EMPTY);
        MethodSignature signature = ((PsiMethod) member).getSignature(superSubstitutor);
        final PsiMethod superClassMethod = MethodSignatureUtil.findMethodBySignature(superClass, signature, false);
        isConflict = superClassMethod != null;
      }

      if (isConflict) {
        LocalizeValue message = RefactoringLocalize.zeroAlreadyContainsA1(
          RefactoringUIUtil.getDescription(superClass, false),
          RefactoringUIUtil.getDescription(member, false)
        );
        conflictsList.putValue(superClass, CommonRefactoringUtil.capitalize(message.get()));
      }

      if (member instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)member;
        final PsiModifierList modifierList = method.getModifierList();
        if (!modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
          for (PsiClass subClass : ClassInheritorsSearch.search(superClass)) {
            if (method.getContainingClass() != subClass) {
              MethodSignature signature = ((PsiMethod) member).getSignature(TypeConversionUtil.getSuperClassSubstitutor(superClass, subClass, PsiSubstitutor.EMPTY));
              final PsiMethod wouldBeOverriden = MethodSignatureUtil.findMethodBySignature(subClass, signature, false);
              if (wouldBeOverriden != null && VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(wouldBeOverriden.getModifierList()),
                                                                     VisibilityUtil.getVisibilityModifier(modifierList)) > 0) {
                conflictsList.putValue(wouldBeOverriden, CommonRefactoringUtil.capitalize(RefactoringUIUtil.getDescription(method, true) + " in super class would clash with local method from " + RefactoringUIUtil.getDescription(subClass, true)));
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
      if (movedMembers.contains(parent)) return true;
      parent = parent.getParent();
    }
    return false;
  }

  private static class ConflictingUsagesOfSuperClassMemebers extends ClassMemberReferencesVisitor {

    private PsiMember myMember;
    private PsiClass mySubClass;
    private PsiJavaPackage myTargetPackage;
    private Set<PsiMember> myMovedMembers;
    private MultiMap<PsiElement, String> myConflicts;

    public ConflictingUsagesOfSuperClassMemebers(PsiMember member, PsiClass aClass,
                                                 PsiJavaPackage targetPackage,
                                                 Set<PsiMember> movedMembers,
                                                 MultiMap<PsiElement, String> conflicts) {
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
        final PsiClass containingClass = classMember.getContainingClass();
        if (containingClass != null) {
          if (!PsiUtil.isAccessibleFromPackage(classMember, myTargetPackage)) {
            if (classMember.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
              myConflicts.putValue(myMember, RefactoringUIUtil.getDescription(classMember, true) + " won't be accessible");
            }
            else if (classMember.hasModifierProperty(PsiModifier.PROTECTED) && !mySubClass.isInheritor(containingClass, true)) {
              myConflicts.putValue(myMember, RefactoringUIUtil.getDescription(classMember, true) + " won't be accessible");
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
    private final MultiMap<PsiElement, String> myConflictsList;
    private final InterfaceContainmentVerifier myInterfaceContainmentVerifier;

    ConflictingUsagesOfSubClassMembers(PsiElement scope,
                                       Set<PsiMember> movedMembers, Set<PsiMethod> abstractMethods,
                                       PsiClass subclass, PsiClass superClass,
                                       PsiJavaPackage targetPackage, MultiMap<PsiElement, String> conflictsList,
                                       InterfaceContainmentVerifier interfaceContainmentVerifier) {
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

    protected void visitClassMemberReferenceElement(PsiMember classMember,
                                                    PsiJavaCodeReferenceElement classMemberReference) {
      if (classMember != null
          && RefactoringHierarchyUtil.isMemberBetween(mySuperClass, mySubclass, classMember)) {
        if (classMember.hasModifierProperty(PsiModifier.STATIC)
            && !willBeMoved(classMember, myMovedMembers)) {
          final boolean isAccessible;
          if (mySuperClass != null) {
            isAccessible = PsiUtil.isAccessible(classMember, mySuperClass, null);
          }
          else if (myTargetPackage != null) {
            isAccessible = PsiUtil.isAccessibleFromPackage(classMember, myTargetPackage);
          }
          else {
            isAccessible = classMember.hasModifierProperty(PsiModifier.PUBLIC);
          }
          if (!isAccessible) {
            LocalizeValue message = RefactoringLocalize.zeroUses1WhichIsNotAccessibleFromTheSuperclass(
              RefactoringUIUtil.getDescription(myScope, false),
              RefactoringUIUtil.getDescription(classMember, true)
            );
            myConflictsList.putValue(classMember, CommonRefactoringUtil.capitalize(message.get()));
          }
          return;
        }
        if (!myAbstractMethods.contains(classMember) && !willBeMoved(classMember, myMovedMembers)) {
          if (!existsInSuperClass(classMember)) {
            LocalizeValue message = RefactoringLocalize.zeroUses1WhichIsNotMovedToTheSuperclass(
              RefactoringUIUtil.getDescription(myScope, false),
              RefactoringUIUtil.getDescription(classMember, true)
            );
            myConflictsList.putValue(classMember, CommonRefactoringUtil.capitalize(message.get()));
          }
        }
      }
    }



    private boolean existsInSuperClass(PsiElement classMember) {
      if (!(classMember instanceof PsiMethod)) return false;
      final PsiMethod method = ((PsiMethod)classMember);
      if (myInterfaceContainmentVerifier.checkedInterfacesContain(method)) return true;
      if (mySuperClass == null) return false;
      final PsiMethod methodBySignature = mySuperClass.findMethodBySignature(method, true);
      return methodBySignature != null;
    }
  }


}
