/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.chartostring;

import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;

class CharToStringPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiLiteralExpression)) {
      return false;
    }
    PsiLiteralExpression expression =
      (PsiLiteralExpression)element;
    PsiType type = expression.getType();
    if (!PsiType.CHAR.equals(type)) {
      return false;
    }
    String charLiteral = element.getText();
    if (charLiteral.length() < 2) return false; // Incomplete char literal probably without closing amp

    String charText =
      charLiteral.substring(1, charLiteral.length() - 1);
    if (StringUtil.unescapeStringCharacters(charText).length() != 1) {
      // not satisfied with character literals of more than one character
      return false;
    }
    return isInConcatenationContext(expression);
  }

  private static boolean isInConcatenationContext(PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression parentExpression =
        (PsiPolyadicExpression)parent;
      PsiType parentType = parentExpression.getType();
      if (parentType == null) {
        return false;
      }
      String parentTypeText = parentType.getCanonicalText();
      return CommonClassNames.JAVA_LANG_STRING.equals(parentTypeText);
    }
    else if (parent instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression parentExpression =
        (PsiAssignmentExpression)parent;
      IElementType tokenType = parentExpression.getOperationTokenType();
      if (!JavaTokenType.PLUSEQ.equals(tokenType)) {
        return false;
      }
      PsiType parentType = parentExpression.getType();
      if (parentType == null) {
        return false;
      }
      String parentTypeText = parentType.getCanonicalText();
      return CommonClassNames.JAVA_LANG_STRING.equals(parentTypeText);
    }
    else if (parent instanceof PsiExpressionList) {
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression methodCall =
        (PsiMethodCallExpression)grandParent;
      PsiReferenceExpression methodExpression =
        methodCall.getMethodExpression();
      PsiExpression qualifierExpression =
        methodExpression.getQualifierExpression();
      PsiType type;
      if (qualifierExpression == null) {
        // to use the intention inside the source of
        // String and StringBuffer
        type = methodExpression.getType();
      }
      else {
        type = qualifierExpression.getType();
      }
      if (type == null) {
        return false;
      }
      String className = type.getCanonicalText();
      if (CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(className) ||
          CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(className)) {
        @NonNls String methodName =
          methodExpression.getReferenceName();
        if (!"append".equals(methodName) &&
            !"insert".equals(methodName)) {
          return false;
        }
        PsiElement method = methodExpression.resolve();
        return method != null;
      }
      else if (CommonClassNames.JAVA_LANG_STRING.equals(className)) {
        @NonNls String methodName =
          methodExpression.getReferenceName();
        if (!"indexOf".equals(methodName) &&
            !"lastIndexOf".equals(methodName) &&
            !"replace".equals(methodName)) {
          return false;
        }
        PsiElement method = methodExpression.resolve();
        return method != null;
      }
    }
    return false;
  }
}
