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
package com.intellij.java.analysis.impl.refactoring.util.duplicates;

import consulo.language.editor.PsiEquivalenceUtil;
import com.intellij.java.language.psi.*;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class ExpressionReturnValue implements ReturnValue {
  private final PsiExpression myExpression;

  public ExpressionReturnValue(PsiExpression expression) {
    myExpression = expression;
  }

  public PsiExpression getExpression() {
    return myExpression;
  }

  public boolean isEquivalent(ReturnValue other) {
    if (!(other instanceof ExpressionReturnValue)) return false;
    return PsiEquivalenceUtil.areElementsEquivalent(myExpression, ((ExpressionReturnValue)other).myExpression);
  }

  public PsiStatement createReplacement(final PsiMethod extractedMethod, final PsiMethodCallExpression methodCallExpression)
    throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(methodCallExpression.getProject()).getElementFactory();
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(methodCallExpression.getProject());
    PsiExpressionStatement expressionStatement;
    expressionStatement = (PsiExpressionStatement)elementFactory.createStatementFromText("x = y();", null);
    expressionStatement = (PsiExpressionStatement)styleManager.reformat(expressionStatement);
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expressionStatement.getExpression();
    assignmentExpression.getLExpression().replace(getExpression());
    assignmentExpression.getRExpression().replace(methodCallExpression);
    return expressionStatement;
  }
}
