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

import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.language.psi.*;
import com.intellij.java.impl.refactoring.psi.MutationUtils;
import com.intellij.java.impl.refactoring.util.FixableUsageInfo;
import consulo.language.util.IncorrectOperationException;

public class ReplaceInstanceVariableAssignment extends FixableUsageInfo {
  private final String setterName;
  private final PsiAssignmentExpression assignment;
  private final String getterName;
  private final String delegateName;
  private final String fieldName;

  public ReplaceInstanceVariableAssignment(PsiAssignmentExpression assignment,
                                           String delegateName,
                                           String setterName,
                                           String getterName, String name) {
    super(assignment);
    this.assignment = assignment;
    this.getterName = getterName;
    this.setterName = setterName;
    this.delegateName = delegateName;
    fieldName = name;
  }

  public void fixUsage() throws IncorrectOperationException {
    PsiReferenceExpression lhs =
      (PsiReferenceExpression)assignment.getLExpression();
    PsiExpression rhs = assignment.getRExpression();
    assert rhs != null;
    PsiElement qualifier = lhs.getQualifier();
    PsiJavaToken sign = assignment.getOperationSign();
    String operator = sign.getText();
    String rhsText = rhs.getText();
    String newExpression;
    if (qualifier != null) {
      String qualifierText = qualifier.getText();
      if ("=".equals(operator)) {
        newExpression = qualifierText + '.' + delegateName + '.' + callSetter(rhsText);
      }
      else {
        String strippedOperator = getStrippedOperator(operator);
        newExpression = qualifierText +
                        '.' +
                        delegateName +
                        '.' +
                        callSetter(
                          qualifierText +
                          '.' +
                          delegateName +
                          '.' +
                          callGetter() + strippedOperator + rhsText);
      }
    }
    else {
      if ("=".equals(operator)) {
        newExpression = delegateName + '.' + callSetter(rhsText);
      }
      else {
        String strippedOperator = getStrippedOperator(operator);
        newExpression = delegateName + '.' + callSetter(delegateName + '.' + callGetter() + strippedOperator + rhsText);
      }
    }
    MutationUtils.replaceExpression(newExpression, assignment);
  }

  private String callSetter(String rhsText) {
    return setterName != null ? setterName + "( " + rhsText + ")" : fieldName + "=" + rhsText;
  }

  private String callGetter() {
    return getterName != null ? getterName + "()" : fieldName;
  }

  private static String getStrippedOperator(String operator) {
    return operator.substring(0, operator.length() - 1);
  }
}
