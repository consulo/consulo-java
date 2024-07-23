/*
 * Copyright 2011-2013 Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

import java.util.HashSet;
import java.util.Set;

public abstract class UnnecessaryExplicitNumericCastInspection extends BaseInspection {

  private static final Set<IElementType> binaryPromotionOperators = new HashSet();

  static {
    binaryPromotionOperators.add(JavaTokenType.ASTERISK);
    binaryPromotionOperators.add(JavaTokenType.DIV);
    binaryPromotionOperators.add(JavaTokenType.PERC);
    binaryPromotionOperators.add(JavaTokenType.PLUS);
    binaryPromotionOperators.add(JavaTokenType.MINUS);
    binaryPromotionOperators.add(JavaTokenType.LT);
    binaryPromotionOperators.add(JavaTokenType.LE);
    binaryPromotionOperators.add(JavaTokenType.GT);
    binaryPromotionOperators.add(JavaTokenType.GE);
    binaryPromotionOperators.add(JavaTokenType.EQEQ);
    binaryPromotionOperators.add(JavaTokenType.NE);
    binaryPromotionOperators.add(JavaTokenType.AND);
    binaryPromotionOperators.add(JavaTokenType.XOR);
    binaryPromotionOperators.add(JavaTokenType.OR);
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsLocalize.unnecessaryExplicitNumericCastDisplayName().get();
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression) infos[0];
    return InspectionGadgetsLocalize.unnecessaryExplicitNumericCastProblemDescriptor(expression.getText()).get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryExplicitNumericCastFix();
  }

  private static class UnnecessaryExplicitNumericCastFix extends InspectionGadgetsFix {

    @Nonnull
    @Override
    public String getName() {
      return InspectionGadgetsLocalize.unnecessaryExplicitNumericCastQuickfix().get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiTypeCastExpression)) {
        return;
      }
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression) parent;
      if (isPrimitiveNumericCastNecessary(typeCastExpression)) {
        return;
      }
      final PsiExpression operand = typeCastExpression.getOperand();
      if (operand == null) {
        typeCastExpression.delete();
      } else {
        typeCastExpression.replace(operand);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryExplicitNumericCastVisitor();
  }

  private static class UnnecessaryExplicitNumericCastVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      final PsiType castType = expression.getType();
      if (!ClassUtils.isPrimitiveNumericType(castType)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiType operandType = operand.getType();
      if (operandType == null || operandType.equals(castType)) {
        return;
      }
      if (isPrimitiveNumericCastNecessary(expression)) {
        return;
      }
      final PsiTypeElement typeElement = expression.getCastType();
      if (typeElement != null) {
        registerError(typeElement, ProblemHighlightType.LIKE_UNUSED_SYMBOL, operand);
      }
    }
  }

  public static boolean isPrimitiveNumericCastNecessary(PsiTypeCastExpression expression) {
    final PsiType castType = expression.getType();
    if (castType == null) {
      return true;
    }
    final PsiExpression operand = expression.getOperand();
    if (operand == null) {
      return true;
    }
    final PsiType operandType = operand.getType();
    PsiElement parent = expression.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      parent = parent.getParent();
    }
    if (parent instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) parent;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (binaryPromotionOperators.contains(tokenType)) {
        if (PsiType.INT.equals(castType)) {
          return PsiType.LONG.equals(operandType) || PsiType.FLOAT.equals(operandType) || PsiType.DOUBLE.equals(operandType);
        }
        if (PsiType.LONG.equals(castType) || PsiType.FLOAT.equals(castType) || PsiType.DOUBLE.equals(castType)) {
          final PsiExpression[] operands = polyadicExpression.getOperands();
          for (PsiExpression operand1 : operands) {
            if (!PsiTreeUtil.isAncestor(operand1, expression, false)) {
              final PsiType type = operand1.getType();
              if (castType.equals(type)) {
                return false;
              }
            }
          }
        }
      } else if (JavaTokenType.GTGT.equals(tokenType) || JavaTokenType.GTGTGT.equals(tokenType) || JavaTokenType.LTLT.equals(tokenType)) {
        final PsiExpression firstOperand = polyadicExpression.getOperands()[0];
        if (!PsiTreeUtil.isAncestor(firstOperand, expression, false)) {
          return false;
        }
        return PsiType.LONG.equals(castType) || !isLegalWideningConversion(operand, PsiType.INT);
      }
      return true;
    } else if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) parent;
      final PsiType lhsType = assignmentExpression.getType();
      return !castType.equals(lhsType) || !isLegalAssignmentConversion(operand, lhsType);
    } else if (parent instanceof PsiVariable) {
      final PsiVariable variable = (PsiVariable) parent;
      final PsiType lhsType = variable.getType();
      return !castType.equals(lhsType) || !isLegalAssignmentConversion(operand, lhsType);
    } else if (parent instanceof PsiExpressionList) {
      final PsiExpressionList expressionList = (PsiExpressionList) parent;
      final PsiElement grandParent = expressionList.getParent();
      if (!(grandParent instanceof PsiCallExpression)) {
        return true;
      }
      final PsiCallExpression callExpression = (PsiCallExpression) grandParent;
      final PsiMethod targetMethod = callExpression.resolveMethod();
      if (targetMethod == null) {
        return true;
      }
      final PsiElement[] children = callExpression.getChildren();
      final StringBuilder newMethodCallText = new StringBuilder();
      for (PsiElement child : children) {
        if (child != expressionList) {
          newMethodCallText.append(child.getText());
          continue;
        }
        newMethodCallText.append('(');
        final PsiExpression[] arguments = expressionList.getExpressions();
        boolean comma = false;
        for (PsiExpression argument : arguments) {
          if (comma) {
            newMethodCallText.append(',');
          } else {
            comma = true;
          }
          if (PsiTreeUtil.isAncestor(argument, expression, false)) {
            newMethodCallText.append(operand.getText());
          } else {
            newMethodCallText.append(argument.getText());
          }
        }
        newMethodCallText.append(')');
      }
      final Project project = expression.getProject();
      final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = javaPsiFacade.getElementFactory();
      final PsiCallExpression newMethodCall = (PsiCallExpression)
          factory.createExpressionFromText(newMethodCallText.toString(), expression);
      if (targetMethod != newMethodCall.resolveMethod()) {
        return true;
      }
    }
    final PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false);
    return !castType.equals(expectedType) || !isLegalWideningConversion(operand, castType);
  }

  static boolean isLegalWideningConversion(PsiExpression expression, PsiType requiredType) {
    final PsiType operandType = expression.getType();
    if (PsiType.DOUBLE.equals(requiredType)) {
      if (PsiType.FLOAT.equals(operandType) ||
          PsiType.LONG.equals(operandType) ||
          PsiType.INT.equals(operandType) ||
          PsiType.CHAR.equals(operandType) ||
          PsiType.SHORT.equals(operandType) ||
          PsiType.BYTE.equals(operandType)) {
        return true;
      }
    } else if (PsiType.FLOAT.equals(requiredType)) {
      if (PsiType.LONG.equals(operandType) ||
          PsiType.INT.equals(operandType) ||
          PsiType.CHAR.equals(operandType) ||
          PsiType.SHORT.equals(operandType) ||
          PsiType.BYTE.equals(operandType)) {
        return true;
      }
    } else if (PsiType.LONG.equals(requiredType)) {
      if (PsiType.INT.equals(operandType) ||
          PsiType.CHAR.equals(operandType) ||
          PsiType.SHORT.equals(operandType) ||
          PsiType.BYTE.equals(operandType)) {
        return true;
      }
    } else if (PsiType.INT.equals(requiredType)) {
      if (PsiType.CHAR.equals(operandType) ||
          PsiType.SHORT.equals(operandType) ||
          PsiType.BYTE.equals(operandType)) {
        return true;
      }
    }
    return false;
  }

  static boolean isLegalAssignmentConversion(PsiExpression expression, PsiType assignmentType) {
    // JLS 5.2 Assignment Conversion
    final PsiType operandType = expression.getType();
    if (isLegalWideningConversion(expression, assignmentType)) {
      return true;
    } else if (PsiType.SHORT.equals(assignmentType)) {
      if (PsiType.INT.equals(operandType)) {
        final Object constant = ExpressionUtils.computeConstantExpression(expression);
        if (!(constant instanceof Integer)) {
          return false;
        }
        final int i = ((Integer) constant).intValue();
        if (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) {
          // narrowing
          return true;
        }
      }
    } else if (PsiType.CHAR.equals(assignmentType)) {
      if (PsiType.INT.equals(operandType)) {
        final Object constant = ExpressionUtils.computeConstantExpression(expression);
        if (!(constant instanceof Integer)) {
          return false;
        }
        final int i = ((Integer) constant).intValue();
        if (i >= Character.MIN_VALUE && i <= Character.MAX_VALUE) {
          // narrowing
          return true;
        }
      }
    } else if (PsiType.BYTE.equals(assignmentType)) {
      if (PsiType.INT.equals(operandType)) {
        final Object constant = ExpressionUtils.computeConstantExpression(expression);
        if (!(constant instanceof Integer)) {
          return false;
        }
        final int i = ((Integer) constant).intValue();
        if (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) {
          // narrowing
          return true;
        }
      }
    }
    return false;
  }
}
