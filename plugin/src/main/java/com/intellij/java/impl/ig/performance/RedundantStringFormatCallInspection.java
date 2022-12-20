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
package com.intellij.java.impl.ig.performance;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.*;
import consulo.project.Project;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.ast.IElementType;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import consulo.java.language.module.util.JavaClassNames;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class RedundantStringFormatCallInspection extends BaseInspection {

  @Override
  @Nls
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("redundant.string.format.call.display.name");
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("redundant.string.format.call.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RedundantStringFormatCallFix();
  }

  private static class RedundantStringFormatCallFix
    extends InspectionGadgetsFix {

    @Override
    @Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "redundant.string.format.call.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression lastArgument = arguments[arguments.length - 1];
      methodCallExpression.replace(lastArgument);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantStringFormatCallVisitor();
  }

  private static class RedundantStringFormatCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"format".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length > 2 || arguments.length == 0) {
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
      final String className = aClass.getQualifiedName();
      if (!JavaClassNames.JAVA_LANG_STRING.equals(className)) {
        return;
      }
      final PsiExpression firstArgument = arguments[0];
      final PsiType firstType = firstArgument.getType();
      if (firstType == null || containsPercentN(firstArgument)) {
        return;
      }
      if (firstType.equalsToText(JavaClassNames.JAVA_LANG_STRING) && arguments.length == 1) {
        registerMethodCallError(expression);
      }
      else if (firstType.equalsToText("java.util.Locale")) {
        if (arguments.length != 2) {
          return;
        }
        final PsiExpression secondArgument = arguments[1];
        final PsiType secondType = secondArgument.getType();
        if (secondType == null) {
          return;
        }
        if (secondType.equalsToText(JavaClassNames.JAVA_LANG_STRING)) {
          registerMethodCallError(expression);
        }
      }
    }

    private static boolean containsPercentN(PsiExpression expression) {
      if (expression == null) {
        return false;
      }
      if (expression instanceof PsiLiteralExpression) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expression;
        @NonNls final String expressionText = literalExpression.getText();
        return expressionText.contains("%n");
      }
      if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        if (!tokenType.equals(JavaTokenType.PLUS)) {
          return false;
        }
        final PsiExpression[] operands = polyadicExpression.getOperands();
        for (PsiExpression operand : operands) {
          if (containsPercentN(operand)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
