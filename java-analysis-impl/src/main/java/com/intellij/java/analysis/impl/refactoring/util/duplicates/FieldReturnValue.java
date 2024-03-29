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

import com.intellij.java.language.psi.*;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class FieldReturnValue implements ReturnValue {
  private final PsiField myField;

  public FieldReturnValue(PsiField psiField) {
    myField = psiField;
  }

  public boolean isEquivalent(ReturnValue other) {
    if (!(other instanceof FieldReturnValue)) return false;
    return myField == ((FieldReturnValue)other).myField;
  }

  public PsiField getField() {
    return myField;
  }

  public PsiStatement createReplacement(final PsiMethod extractedMethod, final PsiMethodCallExpression methodCallExpression) throws IncorrectOperationException {

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(methodCallExpression.getProject()).getElementFactory();
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(methodCallExpression.getProject());
    PsiExpressionStatement expressionStatement;
    expressionStatement = (PsiExpressionStatement)elementFactory.createStatementFromText("x = y();", null);
    expressionStatement = (PsiExpressionStatement)styleManager.reformat(expressionStatement);
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expressionStatement.getExpression();
    assignmentExpression.getLExpression().replace(elementFactory.createExpressionFromText(myField.getName(), myField));
    assignmentExpression.getRExpression().replace(methodCallExpression);
    return expressionStatement;

  }
}