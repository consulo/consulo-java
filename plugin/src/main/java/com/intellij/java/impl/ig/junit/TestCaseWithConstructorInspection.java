/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class TestCaseWithConstructorInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "JUnitTestCaseWithNonTrivialConstructors";
  }

  @Nonnull
  @Override
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.testCaseWithConstructorDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return Boolean.TRUE.equals(infos[0])
      ? InspectionGadgetsLocalize.testCaseWithConstructorProblemDescriptorInitializer().get()
      : InspectionGadgetsLocalize.testCaseWithConstructorProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new TestCaseWithConstructorVisitor();
  }

  private static class TestCaseWithConstructorVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      if (!method.isConstructor()) {
        return;
      }
      PsiClass aClass = method.getContainingClass();
      if (!TestUtils.isJUnitTestClass(aClass)) {
        return;
      }
      PsiCodeBlock body = method.getBody();
      if (isTrivial(body)) {
        return;
      }
      registerMethodError(method, Boolean.FALSE);
    }

    @Override
    public void visitClassInitializer(PsiClassInitializer initializer) {
      if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      PsiClass aClass = initializer.getContainingClass();
      if (!TestUtils.isJUnitTestClass(aClass)) {
        return;
      }
      registerClassInitializerError(initializer, Boolean.TRUE);
    }

    private static boolean isTrivial(@Nullable PsiCodeBlock codeBlock) {
      if (codeBlock == null) {
        return true;
      }
      PsiStatement[] statements = codeBlock.getStatements();
      if (statements.length == 0) {
        return true;
      }
      if (statements.length > 1) {
        return false;
      }
      PsiStatement statement = statements[0];
      if (!(statement instanceof PsiExpressionStatement)) {
        return false;
      }
      PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement)statement;
      PsiExpression expression =
        expressionStatement.getExpression();
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)expression;
      PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      String text = methodExpression.getText();
      return PsiKeyword.SUPER.equals(text);
    }
  }
}