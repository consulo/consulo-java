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

import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

public class EqualityToEqualsFix extends InspectionGadgetsFix {

  @Nonnull
  public LocalizeValue getName() {
    return InspectionGadgetsLocalize.objectComparisonReplaceQuickfix();
  }

  public void doFix(Project project, ProblemDescriptor descriptor)
    throws IncorrectOperationException {
    PsiElement comparisonToken = descriptor.getPsiElement();
    PsiBinaryExpression expression = (PsiBinaryExpression)
      comparisonToken.getParent();
    if (expression == null) {
      return;
    }
    boolean negated = false;
    IElementType tokenType = expression.getOperationTokenType();
    if (JavaTokenType.NE.equals(tokenType)) {
      negated = true;
    }
    PsiExpression lhs = expression.getLOperand();
    PsiExpression strippedLhs =
      ParenthesesUtils.stripParentheses(lhs);
    if (strippedLhs == null) {
      return;
    }
    PsiExpression rhs = expression.getROperand();
    PsiExpression strippedRhs =
      ParenthesesUtils.stripParentheses(rhs);
    if (strippedRhs == null) {
      return;
    }
    @NonNls String expString;
    if (ParenthesesUtils.getPrecedence(strippedLhs) >
        ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
      expString = '(' + strippedLhs.getText() + ").equals(" +
                  strippedRhs.getText() + ')';
    }
    else {
      expString = strippedLhs.getText() + ".equals(" +
                  strippedRhs.getText() + ')';
    }
    @NonNls String newExpression;
    if (negated) {
      newExpression = '!' + expString;
    }
    else {
      newExpression = expString;
    }
    replaceExpression(expression, newExpression);
  }
}