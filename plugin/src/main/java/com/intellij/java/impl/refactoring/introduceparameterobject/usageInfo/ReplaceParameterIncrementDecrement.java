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
package com.intellij.java.impl.refactoring.introduceparameterobject.usageInfo;

import com.intellij.java.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.impl.refactoring.psi.MutationUtils;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import consulo.language.util.IncorrectOperationException;

public class ReplaceParameterIncrementDecrement extends FixableUsageInfo {
  private final PsiExpression expression;
  private final String newParameterName;
  private final String parameterSetterName;
  private final String parameterGetterName;

  public ReplaceParameterIncrementDecrement(PsiExpression element,
                                            String newParameterName,
                                            String parameterSetterName,
                                            String parameterGetterName) {

    super(element);
    this.parameterSetterName = parameterSetterName;
    this.parameterGetterName = parameterGetterName;
    this.newParameterName = newParameterName;
    final PsiPrefixExpression prefixExpr = PsiTreeUtil.getParentOfType(element, PsiPrefixExpression.class);
    if (prefixExpr != null) {
      expression = prefixExpr;
    }
    else {
      expression = PsiTreeUtil.getParentOfType(element, PsiPostfixExpression.class);
    }
  }

  public void fixUsage() throws IncorrectOperationException {
    final PsiJavaToken sign = expression instanceof PsiPrefixExpression
                              ? ((PsiPrefixExpression)expression).getOperationSign()
                              : ((PsiPostfixExpression)expression).getOperationSign();
    final String operator = sign.getText();
    final String strippedOperator = operator.substring(0, operator.length() - 1);
    final String newExpression =
      newParameterName + '.' + parameterSetterName + '(' + newParameterName + '.' + parameterGetterName + "()" + strippedOperator + "1)";
    if (expression.getParent() instanceof PsiBinaryExpression) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
      final PsiStatement statement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
      statement.getParent().addBefore(factory.createStatementFromText(newExpression + ";", expression), statement);
      expression.replace(factory.createExpressionFromText(newParameterName + "." + parameterGetterName + "()", expression));
    } else {
      MutationUtils.replaceExpression(newExpression, expression);
    }
  }

}
