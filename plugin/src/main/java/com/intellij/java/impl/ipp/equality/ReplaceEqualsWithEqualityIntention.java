/*
 * Copyright 2003-2005 Dave Griffith
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
package com.intellij.java.impl.ipp.equality;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

import javax.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceEqualsWithEqualityIntention", fileExtensions = "java", categories = {"Java", "Boolean"})
public class ReplaceEqualsWithEqualityIntention extends Intention {

  @Nonnull
  public PsiElementPredicate getElementPredicate() {
    return new EqualsPredicate();
  }

  public void processIntention(PsiElement element)
    throws IncorrectOperationException {
    final PsiMethodCallExpression call =
      (PsiMethodCallExpression)element;
    if (call == null) {
      return;
    }
    final PsiReferenceExpression methodExpression =
      call.getMethodExpression();
    final PsiExpression target = methodExpression.getQualifierExpression();
    if (target == null) {
      return;
    }
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression arg = argumentList.getExpressions()[0];
    final PsiExpression strippedTarget =
      ParenthesesUtils.stripParentheses(target);
    if (strippedTarget == null) {
      return;
    }
    final PsiExpression strippedArg =
      ParenthesesUtils.stripParentheses(arg);
    if (strippedArg == null) {
      return;
    }
    final String strippedArgText;
    if (ParenthesesUtils.getPrecedence(strippedArg) >
        ParenthesesUtils.EQUALITY_PRECEDENCE) {
      strippedArgText = '(' + strippedArg.getText() + ')';
    }
    else {
      strippedArgText = strippedArg.getText();
    }
    final String strippedTargetText;
    if (ParenthesesUtils.getPrecedence(strippedTarget) >
        ParenthesesUtils.EQUALITY_PRECEDENCE) {
      strippedTargetText = '(' + strippedTarget.getText() + ')';
    }
    else {
      strippedTargetText = strippedTarget.getText();
    }
    replaceExpression(strippedTargetText + "==" + strippedArgText, call);
  }
}