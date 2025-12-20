/*
 * Copyright 2010 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.asserttoif;

import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;

class IfStatementPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    PsiJavaToken token = (PsiJavaToken)element;
    if (token.getTokenType() != JavaTokenType.IF_KEYWORD) {
      return false;
    }
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiIfStatement)) {
      return false;
    }
    PsiIfStatement statement = (PsiIfStatement)parent;
    PsiStatement elseBranch = statement.getElseBranch();
    if (elseBranch != null) {
      return false;
    }
    PsiStatement thenBranch = statement.getThenBranch();
    return isThrowNewAssertionError(thenBranch);
  }

  private static boolean isThrowNewAssertionError(PsiElement element) {
    if (element instanceof PsiThrowStatement) {
      PsiThrowStatement throwStatement =
        (PsiThrowStatement)element;
      PsiExpression exception = throwStatement.getException();
      if (!(exception instanceof PsiNewExpression)) {
        return false;
      }
      PsiNewExpression newExpression = (PsiNewExpression)exception;
      PsiJavaCodeReferenceElement classReference =
        newExpression.getClassReference();
      if (classReference == null) {
        return false;
      }
      PsiElement target = classReference.resolve();
      if (!(target instanceof PsiClass)) {
        return false;
      }
      PsiClass aClass = (PsiClass)target;
      String qualifiedName = aClass.getQualifiedName();
      return CommonClassNames.JAVA_LANG_ASSERTION_ERROR.equals(qualifiedName);
    }
    else if (element instanceof PsiBlockStatement) {
      PsiBlockStatement blockStatement =
        (PsiBlockStatement)element;
      PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
      PsiStatement[] statements = codeBlock.getStatements();
      if (statements.length != 1) {
        return false;
      }
      PsiStatement statement = statements[0];
      return isThrowNewAssertionError(statement);
    }
    return false;
  }
}
