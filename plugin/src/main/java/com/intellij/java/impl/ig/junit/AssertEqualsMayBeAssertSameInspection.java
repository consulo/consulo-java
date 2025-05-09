/*
 * Copyright 2008-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.impl.ig.psiutils.ImportUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class AssertEqualsMayBeAssertSameInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.assertequalsMayBeAssertsameDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.assertequalsMayBeAssertsameProblemDescriptor().get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AssertEqualsMayBeAssertSameFix();
  }

  private static class AssertEqualsMayBeAssertSameFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message("assertequals.may.be.assertsame.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = (PsiReferenceExpression)parent;
      final PsiElement grandParent = methodExpression.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass ==  null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (className == null) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null && ImportUtils.addStaticImport(className, "assertSame", methodExpression)) {
        replaceExpression(methodExpression, "assertSame");
      }
      else {
        replaceExpression(methodExpression, className + ".assertSame");
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertEqualsMayBeAssertSameVisitor();
  }

  private static class AssertEqualsMayBeAssertSameVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String name = methodExpression.getReferenceName();
      if (!"assertEquals".equals(name)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 3 && arguments.length != 2) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String qualifiedName = aClass.getQualifiedName();
      if (!"org.junit.Assert".equals(qualifiedName) && !"junit.framework.Assert".equals(qualifiedName)) {
        return;
      }
      final PsiExpression argument1 = arguments[arguments.length - 2];
      if (!couldBeAssertSameArgument(argument1)) {
        return;
      }
      final PsiExpression argument2 = arguments[arguments.length - 1];
      if (!couldBeAssertSameArgument(argument2)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean couldBeAssertSameArgument(PsiExpression expression) {
      final PsiType type = expression.getType();
      if (!(type instanceof PsiClassType)) {
        return false;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass argumentClass = classType.resolve();
      if (argumentClass == null) {
        return false;
      }
      if (!argumentClass.hasModifierProperty(PsiModifier.FINAL)) {
        return false;
      }
      final PsiMethod[] methods = argumentClass.findMethodsByName("equals", true);
      final PsiManager manager = expression.getManager();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(manager.getProject());
      final PsiClass objectClass = psiFacade.findClass(CommonClassNames.JAVA_LANG_OBJECT, argumentClass.getResolveScope());
      if (objectClass == null) {
        return false;
      }
      for (PsiMethod method : methods) {
        final PsiClass containingClass = method.getContainingClass();
        if (!objectClass.equals(containingClass)) {
          return false;
        }
      }
      return true;
    }
  }
}
