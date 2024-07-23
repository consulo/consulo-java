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
package com.intellij.java.impl.ig.j2me;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class PrivateMemberAccessBetweenOuterAndInnerClassInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.privateMemberAccessBetweenOuterAndInnerClassesDisplayName().get();
  }

  @Override
  @Nonnull
  @RequiredReadAction
  protected String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return InspectionGadgetsLocalize.privateMemberAccessBetweenOuterAndInnerClassesProblemDescriptor(aClass.getName()).get();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    final String className = aClass.getName();
    if (infos.length == 1) {
      return new MakePackagePrivateFix(className, true);
    }
    final PsiMember member = (PsiMember)infos[1];
    @NonNls final String memberName;
    if (member instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)member;
      if (method.isConstructor()) {

      }

      memberName = member.getName() + "()";
    }
    else {
      memberName = member.getName();
    }
    @NonNls final String elementName = className + '.' + memberName;
    return new MakePackagePrivateFix(elementName, false);
  }

  private static class MakePackagePrivateFix extends InspectionGadgetsFix {

    private final String elementName;
    private final boolean constructor;

    private MakePackagePrivateFix(String elementName, boolean constructor) {
      this.elementName = elementName;
      this.constructor = constructor;
    }

    @Override
    @Nonnull
    public String getName() {
      return constructor
        ? InspectionGadgetsLocalize.privateMemberAccessBetweenOuterAndInnerClassesMakeConstructorPackageLocalQuickfix(elementName).get()
        : InspectionGadgetsLocalize.privateMemberAccessBetweenOuterAndInnerClassesMakeLocalQuickfix(elementName).get();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (constructor) {
        makeConstructorPackageLocal(project, element);
      }
      else {
        makeMemberPackageLocal(element);
      }
    }

    private static void makeMemberPackageLocal(PsiElement element) {
      final PsiElement parent = element.getParent();
      final PsiReferenceExpression reference =
        (PsiReferenceExpression)parent;
      final PsiModifierListOwner member =
        (PsiModifierListOwner)reference.resolve();
      if (member == null) {
        return;
      }
      final PsiModifierList modifiers = member.getModifierList();
      if (modifiers == null) {
        return;
      }
      modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
      modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
      modifiers.setModifierProperty(PsiModifier.PRIVATE, false);
    }

    private static void makeConstructorPackageLocal(Project project,
                                                    PsiElement element) {
      final PsiNewExpression newExpression =
        PsiTreeUtil.getParentOfType(element,
                                    PsiNewExpression.class);
      if (newExpression == null) {
        return;
      }
      final PsiMethod constructor =
        newExpression.resolveConstructor();
      if (constructor != null) {
        final PsiModifierList modifierList =
          constructor.getModifierList();
        modifierList.setModifierProperty(PsiModifier.PRIVATE,
                                         false);
        return;
      }
      final PsiJavaCodeReferenceElement referenceElement =
        (PsiJavaCodeReferenceElement)element;
      final PsiElement target = referenceElement.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      final PsiElementFactory elementFactory =
        JavaPsiFacade.getElementFactory(project);
      final PsiMethod newConstructor = elementFactory.createConstructor();
      final PsiModifierList modifierList =
        newConstructor.getModifierList();
      modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
      aClass.add(newConstructor);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PrivateMemberAccessFromInnerClassVisior();
  }

  private static class PrivateMemberAccessFromInnerClassVisior
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
     /* if (JspPsiUtil.isInJspFile(expression)) {
        return;
      }     */
      super.visitNewExpression(expression);
      final PsiClass containingClass =
        getContainingContextClass(expression);
      if (containingClass == null) {
        return;
      }
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        final PsiJavaCodeReferenceElement classReference =
          expression.getClassOrAnonymousClassReference();
        if (classReference == null) {
          return;
        }
        final PsiElement target = classReference.resolve();
        if (!(target instanceof PsiClass)) {
          return;
        }
        final PsiClass aClass = (PsiClass)target;
        if (!aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
          return;
        }
        if (aClass.equals(containingClass)) {
          return;
        }
        registerNewExpressionError(expression, aClass);
      }
      else {
        if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
          return;
        }
        final PsiClass aClass = constructor.getContainingClass();
        if (containingClass.equals(aClass)) {
          return;
        }
        registerNewExpressionError(expression, aClass);
      }
    }

    @Override
    public void visitReferenceExpression(
      @Nonnull PsiReferenceExpression expression) {
    /*  if (JspPsiUtil.isInJspFile(expression)) {
        // disable for jsp files IDEADEV-12957
        return;
      }  */
      super.visitReferenceExpression(expression);
      if (expression.getQualifierExpression() == null) {
        return;
      }
      final PsiElement referenceNameElement =
        expression.getReferenceNameElement();
      if (referenceNameElement == null) {
        return;
      }
      final PsiElement containingClass =
        getContainingContextClass(expression);
      if (containingClass == null) {
        return;
      }
      final PsiElement element = expression.resolve();
      if (!(element instanceof PsiMethod || element instanceof PsiField)) {
        return;
      }
      final PsiMember member = (PsiMember)element;
      if (!member.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final PsiClass memberClass =
        ClassUtils.getContainingClass(member);
      if (memberClass == null) {
        return;
      }
      if (memberClass.equals(containingClass)) {
        return;
      }
      registerError(referenceNameElement, memberClass, member);
    }

    @Nullable
    private static PsiClass getContainingContextClass(PsiElement element) {
      final PsiClass aClass =
        ClassUtils.getContainingClass(element);
      if (aClass instanceof PsiAnonymousClass) {
        final PsiAnonymousClass anonymousClass =
          (PsiAnonymousClass)aClass;
        final PsiExpressionList args = anonymousClass.getArgumentList();
        if (args != null &&
            PsiTreeUtil.isAncestor(args, element, true)) {
          return ClassUtils.getContainingClass(aClass);
        }
      }
      return aClass;
    }
  }
}
