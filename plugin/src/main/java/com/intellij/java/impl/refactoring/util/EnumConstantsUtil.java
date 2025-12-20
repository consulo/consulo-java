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

/*
 * User: anna
 * Date: 03-Sep-2008
 */
package com.intellij.java.impl.refactoring.util;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nullable;

import java.util.Set;

public class EnumConstantsUtil {
  private EnumConstantsUtil() {
  }

  public static boolean isSuitableForEnumConstant(PsiType constantType, PsiClass enumClass) {
    if (enumClass != null && enumClass.isEnum()) {
      for (PsiMethod constructor : enumClass.getConstructors()) {
        PsiParameter[] parameters = constructor.getParameterList().getParameters();
        if (parameters.length == 1 && TypeConversionUtil.isAssignable(parameters[0].getType(), constantType)) return true;
      }
    }
    return false;
  }

  public static PsiEnumConstant createEnumConstant(PsiClass enumClass, String constantName, PsiExpression initializerExpr) throws
                                                                                                                              IncorrectOperationException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(enumClass.getProject()).getElementFactory();
    String enumConstantText = initializerExpr != null ? constantName + "(" + initializerExpr.getText() + ")" : constantName;
    return elementFactory.createEnumConstantFromText(enumConstantText, enumClass);
  }

  public static PsiEnumConstant createEnumConstant(PsiClass enumClass, PsiLocalVariable local, String fieldName) throws IncorrectOperationException {
    return createEnumConstant(enumClass, fieldName, local.getInitializer());
  }

  @Nullable
  public static PsiStatement isEnumSwitch(PsiSwitchStatement switchStatement,
                                          PsiType enumValueType,
                                          Set<Object> enumValues) {
    PsiExpression expression = switchStatement.getExpression();
    if (expression != null) {
      PsiType expressionType = expression.getType();
      if (expressionType != null && !TypeConversionUtil.isAssignable(expressionType, enumValueType)) {
        return switchStatement;
      }
      PsiConstantEvaluationHelper evaluationHelper =
        JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper();
      PsiCodeBlock body = switchStatement.getBody();
      if (body != null) {
        for (PsiStatement statement : body.getStatements()) {
          if (statement instanceof PsiSwitchLabelStatement) {
            PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement)statement;
            Object caseValue = evaluationHelper.computeConstantExpression(labelStatement.getCaseValue());
            if (caseValue != null && !enumValues.contains(caseValue)) return statement;
          }
        }
        return null;
      }
    }
    return switchStatement;
  }
}