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
package com.intellij.java.impl.ig.fixes;

import javax.annotation.Nonnull;

import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiExpression;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;

public class EqualityToEqualsFix extends InspectionGadgetsFix {

  @Nonnull
  public String getName() {
    return InspectionGadgetsBundle.message(
      "object.comparison.replace.quickfix");
  }

  public void doFix(Project project, ProblemDescriptor descriptor)
    throws IncorrectOperationException {
    final PsiElement comparisonToken = descriptor.getPsiElement();
    final PsiBinaryExpression expression = (PsiBinaryExpression)
      comparisonToken.getParent();
    if (expression == null) {
      return;
    }
    boolean negated = false;
    final IElementType tokenType = expression.getOperationTokenType();
    if (JavaTokenType.NE.equals(tokenType)) {
      negated = true;
    }
    final PsiExpression lhs = expression.getLOperand();
    final PsiExpression strippedLhs =
      ParenthesesUtils.stripParentheses(lhs);
    if (strippedLhs == null) {
      return;
    }
    final PsiExpression rhs = expression.getROperand();
    final PsiExpression strippedRhs =
      ParenthesesUtils.stripParentheses(rhs);
    if (strippedRhs == null) {
      return;
    }
    @NonNls final String expString;
    if (ParenthesesUtils.getPrecedence(strippedLhs) >
        ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
      expString = '(' + strippedLhs.getText() + ").equals(" +
                  strippedRhs.getText() + ')';
    }
    else {
      expString = strippedLhs.getText() + ".equals(" +
                  strippedRhs.getText() + ')';
    }
    @NonNls final String newExpression;
    if (negated) {
      newExpression = '!' + expString;
    }
    else {
      newExpression = expString;
    }
    replaceExpression(expression, newExpression);
  }
}