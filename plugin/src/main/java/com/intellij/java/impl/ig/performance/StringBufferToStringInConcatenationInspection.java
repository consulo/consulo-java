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

import jakarta.annotation.Nonnull;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import consulo.java.language.module.util.JavaClassNames;

@ExtensionImpl
public class StringBufferToStringInConcatenationInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "string.buffer.to.string.in.concatenation.display.name");
  }

  @Override
  @jakarta.annotation.Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "string.buffer.to.string.in.concatenation.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringBufferToStringVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new StringBufferToStringFix();
  }

  private static class StringBufferToStringFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "string.buffer.to.string.in.concatenation.remove.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodNameToken = descriptor.getPsiElement();
      final PsiElement methodCallExpression = methodNameToken.getParent();
      assert methodCallExpression != null;
      final PsiMethodCallExpression methodCall =
        (PsiMethodCallExpression)methodCallExpression.getParent();
      assert methodCall != null;
      final PsiReferenceExpression expression =
        methodCall.getMethodExpression();
      final PsiExpression qualifier = expression.getQualifierExpression();
      assert qualifier != null;
      final String newExpression = qualifier.getText();
      replaceExpression(methodCall, newExpression);
    }
  }

  private static class StringBufferToStringVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!ExpressionUtils.isStringConcatenationOperand(expression)) {
        return;
      }
      if (!isStringBufferToString(expression)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean isStringBufferToString(
      PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.TO_STRING.equals(referenceName)) {
        return false;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final String className = aClass.getQualifiedName();
      return JavaClassNames.JAVA_LANG_STRING_BUFFER.equals(className) ||
             JavaClassNames.JAVA_LANG_STRING_BUILDER.equals(className);
    }
  }
}