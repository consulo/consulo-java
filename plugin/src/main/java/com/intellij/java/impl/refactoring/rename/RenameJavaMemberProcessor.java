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
package com.intellij.java.impl.refactoring.rename;

import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.application.util.function.Processor;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.refactoring.rename.RenamePsiElementProcessor;
import consulo.language.psi.*;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.usage.UsageInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public abstract class RenameJavaMemberProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance(RenameJavaMemberProcessor.class);

  public static void qualifyMember(PsiMember member, PsiElement occurrence, String newName) throws IncorrectOperationException {
    qualifyMember(occurrence, newName, member.getContainingClass(), member.hasModifierProperty(PsiModifier.STATIC));
  }

  protected static void qualifyMember(PsiElement occurrence, String newName, PsiClass containingClass, boolean isStatic)
      throws IncorrectOperationException {
    PsiManager psiManager = occurrence.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    if (isStatic) {
      PsiReferenceExpression qualified = (PsiReferenceExpression)factory.createExpressionFromText("a." + newName, null);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      qualified.getQualifierExpression().replace(factory.createReferenceExpression(containingClass));
      occurrence.replace(qualified);
    }
    else {
      PsiReferenceExpression qualified = createQualifiedMemberReference(occurrence, newName, containingClass, isStatic);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      occurrence.replace(qualified);
    }
  }

  public static PsiReferenceExpression createMemberReference(PsiMember member, PsiElement context) throws IncorrectOperationException {
    PsiManager manager = member.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    String name = member.getName();
    PsiReferenceExpression ref = (PsiReferenceExpression) factory.createExpressionFromText(name, context);
    PsiElement resolved = ref.resolve();
    if (manager.areElementsEquivalent(resolved, member)) return ref;
    return createQualifiedMemberReference(context, name, member.getContainingClass(), member.hasModifierProperty(PsiModifier.STATIC));
  }

  protected static PsiReferenceExpression createQualifiedMemberReference(PsiElement context, String name,
                                                                         PsiClass containingClass, boolean isStatic) throws IncorrectOperationException {
    PsiReferenceExpression ref;
    PsiJavaCodeReferenceElement qualifier;

    PsiManager manager = containingClass.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    if (isStatic) {
      ref = (PsiReferenceExpression)factory.createExpressionFromText("A." + name, context);
      qualifier = (PsiJavaCodeReferenceElement)ref.getQualifierExpression();
      PsiReferenceExpression classReference = factory.createReferenceExpression(containingClass);
      qualifier.replace(classReference);
    }
    else {
      PsiClass contextClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
      if (InheritanceUtil.isInheritorOrSelf(contextClass, containingClass, true)) {
        ref = (PsiReferenceExpression)factory.createExpressionFromText("this." + name, context);
        return ref;
      }

      while (contextClass != null && !InheritanceUtil.isInheritorOrSelf(contextClass, containingClass, true)) {
        contextClass = PsiTreeUtil.getParentOfType(contextClass, PsiClass.class, true);
      }

      ref = (PsiReferenceExpression) factory.createExpressionFromText("A.this." + name, null);
      qualifier = ((PsiThisExpression)ref.getQualifierExpression()).getQualifier();
      PsiJavaCodeReferenceElement classReference = factory.createClassReferenceElement(contextClass != null ? contextClass : containingClass);
      qualifier.replace(classReference);
    }
    return ref;
  }

  protected static void findMemberHidesOuterMemberCollisions(final PsiMember member, String newName, final List<UsageInfo> result) {
    if (member instanceof PsiCompiledElement) return;
    PsiMember patternMember;
    if (member instanceof PsiMethod) {
      PsiMethod patternMethod = (PsiMethod) member.copy();
      try {
        patternMethod.setName(newName);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return;
      }
      patternMember = patternMethod;
    }
    else {
      patternMember = member;
    }

    final PsiClass fieldClass = member.getContainingClass();
    for (PsiClass aClass = fieldClass != null ? fieldClass.getContainingClass() : null; aClass != null; aClass = aClass.getContainingClass()) {
      PsiMember conflict;
      if (member instanceof PsiMethod) {
        conflict = aClass.findMethodBySignature((PsiMethod)patternMember, true);
      }
      else {
        conflict = aClass.findFieldByName(newName, false);
      }
      if (conflict == null) continue;
      ReferencesSearch.search(conflict).forEach(new Processor<PsiReference>() {
        public boolean process(PsiReference reference) {
          PsiElement refElement = reference.getElement();
          if (refElement instanceof PsiReferenceExpression && ((PsiReferenceExpression)refElement).isQualified()) return true;
          if (PsiTreeUtil.isAncestor(fieldClass, refElement, false)) {
            MemberHidesOuterMemberUsageInfo info = new MemberHidesOuterMemberUsageInfo(refElement, member);
            result.add(info);
          }
          return true;
        }
      });
    }
  }

  protected static void qualifyOuterMemberReferences(List<MemberHidesOuterMemberUsageInfo> outerHides) throws IncorrectOperationException {
    for (MemberHidesOuterMemberUsageInfo usage : outerHides) {
      PsiElement element = usage.getElement();
      PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)element;
      PsiMember member = (PsiMember)usage.getReferencedElement();
      PsiReferenceExpression ref = createMemberReference(member, collidingRef);
      collidingRef.replace(ref);
    }
  }

  protected static void findCollisionsAgainstNewName(PsiMember memberToRename, String newName, List<? super MemberHidesStaticImportUsageInfo> result) {
    if (!memberToRename.isPhysical()) {
      return;
    }
    final List<PsiReference> potentialConflicts = new ArrayList<PsiReference>();
    PsiFile containingFile = memberToRename.getContainingFile();
    if (containingFile instanceof PsiJavaFile) {
      PsiImportList importList = ((PsiJavaFile)containingFile).getImportList();
      if (importList != null) {
        for (PsiImportStaticStatement staticImport : importList.getImportStaticStatements()) {
          String referenceName = staticImport.getReferenceName();
          if (referenceName != null && !referenceName.equals(newName)) {
            continue;
          }
          PsiClass targetClass = staticImport.resolveTargetClass();
          if (targetClass != null) {
            Set<PsiMember> importedMembers = new HashSet<PsiMember>();
            if (memberToRename instanceof PsiMethod) {
              for (PsiMethod method : targetClass.findMethodsByName(newName, true)) {
                if (method.getModifierList().hasModifierProperty(PsiModifier.STATIC)) {
                  importedMembers.add(method);
                }
              }
            }
            else if (memberToRename instanceof PsiField) {
              PsiField fieldByName = targetClass.findFieldByName(newName, true);
              if (fieldByName != null) {
                importedMembers.add(fieldByName);
              }
            }

            for (PsiMember member : importedMembers) {
              ReferencesSearch.search(member, new LocalSearchScope(containingFile), true).forEach(new Processor<PsiReference>() {
                public boolean process(PsiReference psiReference) {
                  potentialConflicts.add(psiReference);
                  return true;
                }
              });
            }
          }
        }
      }
    }

    for (PsiReference potentialConflict : potentialConflicts) {
      if (potentialConflict instanceof PsiJavaReference) {
        JavaResolveResult resolveResult = ((PsiJavaReference)potentialConflict).advancedResolve(false);
        PsiElement conflictElement = resolveResult.getElement();
        if (conflictElement != null) {
          PsiElement scope = resolveResult.getCurrentFileResolveScope();
          if (scope instanceof PsiImportStaticStatement) {
            result.add(new MemberHidesStaticImportUsageInfo(potentialConflict.getElement(), conflictElement, memberToRename));
          }
        }
      }
    }
  }

  protected static void qualifyStaticImportReferences(List<MemberHidesStaticImportUsageInfo> staticImportHides)
      throws IncorrectOperationException {
    for (MemberHidesStaticImportUsageInfo info : staticImportHides) {
      PsiReference ref = info.getReference();
      if (ref == null) return;
      PsiElement occurrence = ref.getElement();
      PsiElement target = info.getReferencedElement();
      if (target instanceof PsiMember && occurrence != null) {
        PsiMember targetMember = (PsiMember)target;
        PsiClass containingClass = targetMember.getContainingClass();
        qualifyMember(occurrence, targetMember.getName(), containingClass, true);
      }
    }
  }
}
