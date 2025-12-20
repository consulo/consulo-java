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
package com.intellij.java.impl.refactoring.extractclass.usageInfo;

import com.intellij.java.impl.refactoring.psi.MutationUtils;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.collection.ArrayUtil;

public class ReplaceStaticVariableAccess extends FixableUsageInfo {
  private final PsiReferenceExpression expression;
  private final String delegateClass;
  private final boolean myEnumConstant;
  private static final Logger LOGGER = Logger.getInstance(ReplaceStaticVariableAccess.class);

  public ReplaceStaticVariableAccess(PsiReferenceExpression expression, String delegateClass, boolean enumConstant) {
    super(expression);
    this.expression = expression;
    this.delegateClass = delegateClass;
    myEnumConstant = enumConstant;
  }

  public void fixUsage() throws IncorrectOperationException {
    if (myEnumConstant) {
      PsiSwitchLabelStatement switchStatement = PsiTreeUtil.getParentOfType(expression, PsiSwitchLabelStatement.class);
      if (switchStatement != null) {
        MutationUtils.replaceExpression(expression.getReferenceName(), expression);
        return;
      }
    }
    boolean replaceWithGetEnumValue = myEnumConstant && !alreadyMigratedToEnum();
    String link = replaceWithGetEnumValue ? "." + PropertyUtil.suggestGetterName("value", expression.getType()) + "()" : "";
    MutationUtils.replaceExpression(delegateClass + '.' + expression.getReferenceName() + link, expression);
  }

  private boolean alreadyMigratedToEnum() {
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
    if (callExpression != null) {
      PsiElement resolved = callExpression.getMethodExpression().resolve();
      if (resolved instanceof PsiMethod) {
        PsiParameter[] parameters = ((PsiMethod)resolved).getParameterList().getParameters();
        PsiExpression[] args = callExpression.getArgumentList().getExpressions();
        int idx = ArrayUtil.find(args, expression);
        if (idx != -1 && parameters[idx].getType().equalsToText(delegateClass)) {
          return true;
        }
      }
    }
    else {
      PsiReturnStatement returnStatement = PsiTreeUtil.getParentOfType(expression, PsiReturnStatement.class);
      if (returnStatement != null) {
        PsiMethod psiMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
        LOGGER.assertTrue(psiMethod != null);
        PsiType returnType = psiMethod.getReturnType();
        if (returnType != null && returnType.getCanonicalText().equals(delegateClass)) {
          return true;
        }
      } else {
        PsiVariable psiVariable = PsiTreeUtil.getParentOfType(expression, PsiVariable.class);
        if (psiVariable != null) {
          if (psiVariable.getType().equalsToText(delegateClass)) {
            return true;
          }
        } else {
          PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(expression, PsiAssignmentExpression.class);
          if (assignmentExpression != null && assignmentExpression.getRExpression() == expression) {
            PsiExpression lExpression = assignmentExpression.getLExpression();
            if (lExpression instanceof PsiReferenceExpression) {
              PsiElement resolve = ((PsiReferenceExpression)lExpression).resolve();
              if (resolve instanceof PsiVariable && ((PsiVariable)resolve).getType().equalsToText(delegateClass)) {
                return true;
              }
            }
          }
        }
      }
    }
    return false;
  }
}
