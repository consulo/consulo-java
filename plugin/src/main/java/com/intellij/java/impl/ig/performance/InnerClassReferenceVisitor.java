/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import jakarta.annotation.Nonnull;

public class InnerClassReferenceVisitor extends JavaRecursiveElementVisitor {

  private final PsiClass innerClass;
  private boolean referencesStaticallyAccessible = true;

  public InnerClassReferenceVisitor(PsiClass innerClass) {
    this.innerClass = innerClass;
  }

  public boolean canInnerClassBeStatic() {
    return referencesStaticallyAccessible;
  }

  private boolean isClassStaticallyAccessible(PsiClass aClass) {
    if (aClass.getContainingClass() != null &&
        aClass.hasModifierProperty(PsiModifier.STATIC)) {
      if (!PsiTreeUtil.isAncestor(aClass, innerClass, false)) {
        return true;
      }
    }
    if (InheritanceUtil.isInheritorOrSelf(innerClass, aClass, true)) {
      return true;
    }
    PsiClass classScope = aClass;
    final PsiClass outerClass =
      ClassUtils.getContainingClass(innerClass);
    while (classScope != null) {
      if (InheritanceUtil.isInheritorOrSelf(outerClass, classScope,
                                            true)) {
        return false;
      }
      final PsiElement scope = classScope.getScope();
      if (scope instanceof PsiClass) {
        classScope = (PsiClass)scope;
      }
      else {
        classScope = null;
      }
    }
    return true;
  }

  @Override
  public void visitThisExpression(
    @Nonnull PsiThisExpression expression) {
    if (!referencesStaticallyAccessible) {
      return;
    }
    super.visitThisExpression(expression);
    final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
    if (isContainingClassQualifier(qualifier)) {
      referencesStaticallyAccessible = false;
    }
  }

  @Override
  public void visitSuperExpression(
    @Nonnull PsiSuperExpression expression) {
    if (!referencesStaticallyAccessible) {
      return;
    }
    super.visitSuperExpression(expression);
    final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
    if (isContainingClassQualifier(qualifier)) {
      referencesStaticallyAccessible = false;
    }
  }

  private boolean isContainingClassQualifier(
    PsiJavaCodeReferenceElement qualifier) {
    if (qualifier == null) {
      return false;
    }
    final PsiElement element = qualifier.resolve();
    if (!(element instanceof PsiClass)) {
      return false;
    }
    final PsiClass aClass = (PsiClass)element;
    return !aClass.equals(innerClass);
  }

  @Override
  public void visitReferenceElement(
    @Nonnull PsiJavaCodeReferenceElement reference) {
    if (!referencesStaticallyAccessible) {
      return;
    }
    final PsiElement parent = reference.getParent();
    if (parent instanceof PsiThisExpression ||
        parent instanceof PsiSuperExpression) {
      return;
    }
    super.visitReferenceElement(reference);
    final PsiElement element = reference.resolve();
    if (!(element instanceof PsiClass)) {
      return;
    }
    final PsiClass aClass = (PsiClass)element;
    final PsiElement scope = aClass.getScope();
    if (!(scope instanceof PsiClass)) {
      return;
    }
    referencesStaticallyAccessible &=
      aClass.hasModifierProperty(PsiModifier.STATIC);
  }

  @Override
  public void visitReferenceExpression(
    @Nonnull PsiReferenceExpression expression) {
    if (!referencesStaticallyAccessible) {
      return;
    }
    super.visitReferenceExpression(expression);
    final PsiExpression qualifier =
      expression.getQualifierExpression();
    if (qualifier instanceof PsiSuperExpression) {
      return;
    }
    if (qualifier instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)qualifier;
      final PsiElement resolvedExpression = referenceExpression.resolve();
      if (!(resolvedExpression instanceof PsiField) &&
          !(resolvedExpression instanceof PsiMethod)) {
        return;
      }
    }
    final PsiElement element = expression.resolve();
    if (element instanceof PsiMethod || element instanceof PsiField) {
      final PsiMember member = (PsiMember)element;
      if (member.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass containingClass = member.getContainingClass();
      referencesStaticallyAccessible &=
        isClassStaticallyAccessible(containingClass);
    }
    if (element instanceof PsiLocalVariable ||
        element instanceof PsiParameter) {
      final PsiElement containingMethod =
        PsiTreeUtil.getParentOfType(expression,
                                    PsiMethod.class);
      final PsiElement referencedMethod =
        PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (containingMethod != null && referencedMethod != null &&
          !containingMethod.equals(referencedMethod)) {
        referencesStaticallyAccessible = false;
      }
    }
  }
}
