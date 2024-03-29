/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.conditional;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

class ReplaceConditionalWithIfPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiConditionalExpression)) {
      return false;
    }
    final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)element;
    final PsiElement parent = conditionalExpression.getParent();
    if (parent instanceof PsiExpressionStatement) {
      return false;
    }
    /*if (JspPsiUtil.isInJspFile(element)) {
      return false;
    }    */
    final PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
    if (member instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)member;
      if (!method.isConstructor()) {
        return true;
      }
      final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (methodCallExpression == null) {
        return true;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      return !"super".equals(methodName);
    }
    else if (member instanceof PsiField) {
      return false;
    }
    return true;
  }
}