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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.intellij.java.analysis.impl.codeInspection.SideEffectChecker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class ManualArrayCopyInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.manualArrayCopyDisplayName();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.manualArrayCopyProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ManualArrayCopyVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    Boolean decrement = (Boolean)infos[0];
    return new ManualArrayCopyFix(decrement.booleanValue());
  }

  private static class ManualArrayCopyFix extends InspectionGadgetsFix {

    private final boolean decrement;

    public ManualArrayCopyFix(boolean decrement) {
      this.decrement = decrement;
    }

    @Override
    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.manualArrayCopyReplaceQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiElement forElement = descriptor.getPsiElement();
      PsiForStatement forStatement = (PsiForStatement)forElement.getParent();
      String newExpression = buildSystemArrayCopyText(forStatement);
      if (newExpression == null) {
        return;
      }
      replaceStatement(forStatement, newExpression);
    }

    @Nullable
    private String buildSystemArrayCopyText(PsiForStatement forStatement) throws IncorrectOperationException {
      PsiExpression condition = forStatement.getCondition();
      PsiBinaryExpression binaryExpression = (PsiBinaryExpression)ParenthesesUtils.stripParentheses(condition);
      if (binaryExpression == null) {
        return null;
      }
      IElementType tokenType = binaryExpression.getOperationTokenType();
      PsiExpression limit;
      if (decrement ^ JavaTokenType.LT.equals(tokenType) || JavaTokenType.LE.equals(tokenType)) {
        limit = binaryExpression.getROperand();
      }
      else {
        limit = binaryExpression.getLOperand();
      }
      if (limit == null) {
        return null;
      }
      PsiStatement initialization = forStatement.getInitialization();
      if (initialization == null) {
        return null;
      }
      if (!(initialization instanceof PsiDeclarationStatement)) {
        return null;
      }
      PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
      PsiElement[] declaredElements = declaration.getDeclaredElements();
      if (declaredElements.length != 1) {
        return null;
      }
      PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiLocalVariable)) {
        return null;
      }
      PsiLocalVariable variable = (PsiLocalVariable)declaredElement;
      String lengthText;
      PsiExpression initializer = variable.getInitializer();
      if (decrement) {
        lengthText = buildLengthText(initializer, limit, JavaTokenType.LE.equals(tokenType) || JavaTokenType.GE.equals(tokenType));
      }
      else {
        lengthText = buildLengthText(limit, initializer, JavaTokenType.LE.equals(tokenType) || JavaTokenType.GE.equals(tokenType));
      }
      if (lengthText == null) {
        return null;
      }
      PsiArrayAccessExpression lhs = getLhsArrayAccessExpression(forStatement);
      if (lhs == null) {
        return null;
      }
      PsiExpression lArray = lhs.getArrayExpression();
      String toArrayText = lArray.getText();
      PsiArrayAccessExpression rhs = getRhsArrayAccessExpression(forStatement);
      if (rhs == null) {
        return null;
      }
      PsiExpression rArray = rhs.getArrayExpression();
      String fromArrayText = rArray.getText();
      PsiExpression rhsIndexExpression = rhs.getIndexExpression();
      PsiExpression strippedRhsIndexExpression = ParenthesesUtils.stripParentheses(rhsIndexExpression);
      PsiExpression limitExpression;
      if (decrement) {
        limitExpression = limit;
      }
      else {
        limitExpression = initializer;
      }
      String fromOffsetText = buildOffsetText(strippedRhsIndexExpression, variable, limitExpression, decrement &&
                                         (JavaTokenType.LT.equals(tokenType) || JavaTokenType.GT.equals(tokenType)));
      PsiExpression lhsIndexExpression = lhs.getIndexExpression();
      PsiExpression strippedLhsIndexExpression = ParenthesesUtils.stripParentheses(lhsIndexExpression);
      String toOffsetText = buildOffsetText(strippedLhsIndexExpression, variable,
                        limitExpression, decrement && (JavaTokenType.LT.equals(tokenType) || JavaTokenType.GT.equals(tokenType)));
      @NonNls StringBuilder buffer = new StringBuilder(60);
      buffer.append("System.arraycopy(");
      buffer.append(fromArrayText);
      buffer.append(", ");
      buffer.append(fromOffsetText);
      buffer.append(", ");
      buffer.append(toArrayText);
      buffer.append(", ");
      buffer.append(toOffsetText);
      buffer.append(", ");
      buffer.append(lengthText);
      buffer.append(");");
      return buffer.toString();
    }

    @Nullable
    private static PsiArrayAccessExpression getLhsArrayAccessExpression(
      PsiForStatement forStatement) {
      PsiStatement body = forStatement.getBody();
      while (body instanceof PsiBlockStatement) {
        PsiBlockStatement blockStatement =
          (PsiBlockStatement)body;
        PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 2) {
          body = statements[1];
        }
        else if (statements.length == 1) {
          body = statements[0];
        }
        else {
          return null;
        }
      }
      if (!(body instanceof PsiExpressionStatement)) {
        return null;
      }
      PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement)body;
      PsiExpression expression =
        expressionStatement.getExpression();
      if (!(expression instanceof PsiAssignmentExpression)) {
        return null;
      }
      PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)expression;
      PsiExpression lhs = assignmentExpression.getLExpression();

      PsiExpression deparenthesizedExpression =
        ParenthesesUtils.stripParentheses(lhs);
      if (!(deparenthesizedExpression instanceof
              PsiArrayAccessExpression)) {
        return null;
      }
      return (PsiArrayAccessExpression)deparenthesizedExpression;
    }

    @Nullable
    private static PsiArrayAccessExpression getRhsArrayAccessExpression(
      PsiForStatement forStatement) {
      PsiStatement body = forStatement.getBody();
      while (body instanceof PsiBlockStatement) {
        PsiBlockStatement blockStatement =
          (PsiBlockStatement)body;
        PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 1 || statements.length == 2) {
          body = statements[0];
        }
        else {
          return null;
        }
      }
      PsiExpression arrayAccessExpression;
      if (body instanceof PsiDeclarationStatement) {
        PsiDeclarationStatement declarationStatement =
          (PsiDeclarationStatement)body;
        PsiElement[] declaredElements =
          declarationStatement.getDeclaredElements();
        if (declaredElements.length != 1) {
          return null;
        }
        PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable)) {
          return null;
        }
        PsiVariable variable = (PsiVariable)declaredElement;
        arrayAccessExpression = variable.getInitializer();
      }
      else if (body instanceof PsiExpressionStatement) {
        PsiExpressionStatement expressionStatement =
          (PsiExpressionStatement)body;
        PsiExpression expression =
          expressionStatement.getExpression();
        if (!(expression instanceof PsiAssignmentExpression)) {
          return null;
        }
        PsiAssignmentExpression assignmentExpression =
          (PsiAssignmentExpression)expression;
        arrayAccessExpression = assignmentExpression.getRExpression();
      }
      else {
        return null;
      }
      PsiExpression unparenthesizedExpression =
        ParenthesesUtils.stripParentheses(arrayAccessExpression);
      if (!(unparenthesizedExpression instanceof
              PsiArrayAccessExpression)) {
        return null;
      }
      return (PsiArrayAccessExpression)unparenthesizedExpression;
    }

    @NonNls
    @Nullable
    private static String buildLengthText(PsiExpression max, PsiExpression min, boolean plusOne) {
      max = ParenthesesUtils.stripParentheses(max);
      if (max == null) {
        return null;
      }
      min = ParenthesesUtils.stripParentheses(min);
      if (min == null) {
        return buildExpressionText(max, plusOne, false);
      }
      Object minConstant = ExpressionUtils.computeConstantExpression(min);
      if (minConstant instanceof Number) {
        Number minNumber = (Number)minConstant;
        int minValue;
        if (plusOne) {
          minValue = minNumber.intValue() - 1;
        }
        else {
          minValue = minNumber.intValue();
        }
        if (minValue == 0) {
          return buildExpressionText(max, false, false);
        }
        if (max instanceof PsiLiteralExpression) {
          Object maxConstant = ExpressionUtils.computeConstantExpression(max);
          if (maxConstant instanceof Number) {
            Number number = (Number)maxConstant;
            return String.valueOf(number.intValue() - minValue);
          }
        }
        String maxText = buildExpressionText(max, false, false);
        if (minValue > 0) {
          return maxText + '-' + minValue;
        }
        else {
          return maxText + '+' + -minValue;
        }
      }
      int precedence = ParenthesesUtils.getPrecedence(min);
      String minText;
      if (precedence >= ParenthesesUtils.ADDITIVE_PRECEDENCE) {
        minText = '(' + min.getText() + ')';
      }
      else {
        minText = min.getText();
      }
      String maxText = buildExpressionText(max, plusOne, false);
      return maxText + '-' + minText;
    }

    private static String buildExpressionText(PsiExpression expression, boolean plusOne, boolean parenthesize) {
      if (!plusOne) {
        int precedence = ParenthesesUtils.getPrecedence(expression);
        if (precedence > ParenthesesUtils.ADDITIVE_PRECEDENCE) {
          return '(' + expression.getText() + ')';
        }
        else {
          if (parenthesize && precedence >= ParenthesesUtils.ADDITIVE_PRECEDENCE) {
            return '(' + expression.getText() + ')';
          }
          return expression.getText();
        }
      }
      if (expression instanceof PsiBinaryExpression) {
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
        IElementType tokenType = binaryExpression.getOperationTokenType();
        if (tokenType == JavaTokenType.MINUS) {
          PsiExpression rhs = binaryExpression.getROperand();
          if (ExpressionUtils.isOne(rhs)) {
            return binaryExpression.getLOperand().getText();
          }
        }
      }
      else if (expression instanceof PsiLiteralExpression) {
        PsiLiteralExpression literalExpression = (PsiLiteralExpression)expression;
        Object value = literalExpression.getValue();
        if (value instanceof Integer) {
          Integer integer = (Integer)value;
          return String.valueOf(integer.intValue() + 1);
        }
      }
      int precedence = ParenthesesUtils.getPrecedence(expression);
      String result;
      if (precedence > ParenthesesUtils.ADDITIVE_PRECEDENCE) {
        result = '(' + expression.getText() + ")+1";
      }
      else {
        result = expression.getText() + "+1";
      }
      if (parenthesize) {
        return '(' + result + ')';
      }
      return result;
    }

    @NonNls
    @Nullable
    private static String buildOffsetText(PsiExpression expression,
                                          PsiLocalVariable variable,
                                          PsiExpression limitExpression,
                                          boolean plusOne)
      throws IncorrectOperationException {
      if (expression == null) {
        return null;
      }
      String expressionText = expression.getText();
      String variableName = variable.getName();
      if (expressionText.equals(variableName)) {
        PsiExpression initialValue =
          ParenthesesUtils.stripParentheses(limitExpression);
        if (initialValue == null) {
          return null;
        }
        return buildExpressionText(initialValue, plusOne, false);
      }
      else if (expression instanceof PsiBinaryExpression) {
        PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)expression;
        PsiExpression lhs = binaryExpression.getLOperand();
        PsiExpression rhs = binaryExpression.getROperand();
        String rhsText =
          buildOffsetText(rhs, variable, limitExpression, plusOne);
        PsiJavaToken sign = binaryExpression.getOperationSign();
        IElementType tokenType = sign.getTokenType();
        if (ExpressionUtils.isZero(lhs)) {
          if (tokenType.equals(JavaTokenType.MINUS)) {
            return '-' + rhsText;
          }
          return rhsText;
        }
        if (plusOne && tokenType.equals(JavaTokenType.MINUS) &&
            ExpressionUtils.isOne(rhs)) {
          return buildOffsetText(lhs, variable, limitExpression,
                                 false);
        }
        String lhsText = buildOffsetText(lhs, variable,
                                               limitExpression, plusOne);
        if (ExpressionUtils.isZero(rhs)) {
          return lhsText;
        }
        return collapseConstant(lhsText + sign.getText() + rhsText,
                                variable);
      }
      return collapseConstant(expression.getText(), variable);
    }

    private static String collapseConstant(@NonNls String expressionText,
                                           PsiElement context)
      throws IncorrectOperationException {
      Project project = context.getProject();
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      PsiElementFactory factory = psiFacade.getElementFactory();
      PsiExpression fromOffsetExpression =
        factory.createExpressionFromText(expressionText, context);
      Object fromOffsetConstant =
        ExpressionUtils.computeConstantExpression(
          fromOffsetExpression);
      if (fromOffsetConstant != null) {
        return fromOffsetConstant.toString();
      }
      else {
        return expressionText;
      }
    }
  }

  private static class ManualArrayCopyVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(
      @Nonnull PsiForStatement statement) {
      super.visitForStatement(statement);
      PsiStatement initialization = statement.getInitialization();
      if (!(initialization instanceof PsiDeclarationStatement)) {
        return;
      }
      PsiDeclarationStatement declaration =
        (PsiDeclarationStatement)initialization;
      PsiElement[] declaredElements =
        declaration.getDeclaredElements();
      if (declaredElements.length != 1) {
        return;
      }
      PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiLocalVariable)) {
        return;
      }
      PsiLocalVariable variable = (PsiLocalVariable)declaredElement;
      PsiExpression initialValue = variable.getInitializer();
      if (initialValue == null) {
        return;
      }
      PsiStatement update = statement.getUpdate();
      boolean decrement;
      if (VariableAccessUtils.variableIsIncremented(variable, update)) {
        decrement = false;
      }
      else if (VariableAccessUtils.variableIsDecremented(variable,
                                                         update)) {
        decrement = true;
      }
      else {
        return;
      }
      PsiExpression condition = statement.getCondition();
      if (decrement) {
        if (!ExpressionUtils.isVariableGreaterThanComparison(
          condition, variable)) {
          return;
        }
      }
      else {
        if (!ExpressionUtils.isVariableLessThanComparison(
          condition, variable)) {
          return;
        }
      }
      PsiStatement body = statement.getBody();
      if (!bodyIsArrayCopy(body, variable, null)) {
        return;
      }
      registerStatementError(statement, Boolean.valueOf(decrement));
    }

    private static boolean bodyIsArrayCopy(
      PsiStatement body, PsiVariable variable,
      @Nullable PsiVariable variable2) {
      if (body instanceof PsiExpressionStatement) {
        PsiExpressionStatement exp =
          (PsiExpressionStatement)body;
        PsiExpression expression = exp.getExpression();
        return expressionIsArrayCopy(expression, variable, variable2);
      }
      else if (body instanceof PsiBlockStatement) {
        PsiBlockStatement blockStatement =
          (PsiBlockStatement)body;
        PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 1) {
          return bodyIsArrayCopy(statements[0], variable, variable2);
        }
        else if (statements.length == 2) {
          PsiStatement statement = statements[0];
          if (!(statement instanceof PsiDeclarationStatement)) {
            return false;
          }
          PsiDeclarationStatement declarationStatement =
            (PsiDeclarationStatement)statement;
          PsiElement[] declaredElements =
            declarationStatement.getDeclaredElements();
          if (declaredElements.length != 1) {
            return false;
          }
          PsiElement declaredElement = declaredElements[0];
          if (!(declaredElement instanceof PsiVariable)) {
            return false;
          }
          PsiVariable localVariable =
            (PsiVariable)declaredElement;
          PsiExpression initializer =
            localVariable.getInitializer();
          if (!ExpressionUtils.isOffsetArrayAccess(initializer,
                                                   variable)) {
            return false;
          }
          return bodyIsArrayCopy(statements[1], variable,
                                 localVariable);
        }
      }
      return false;
    }

    private static boolean expressionIsArrayCopy(
      @Nullable PsiExpression expression,
      @Nonnull PsiVariable variable,
      @Nullable PsiVariable variable2) {
      PsiExpression strippedExpression =
        ParenthesesUtils.stripParentheses(expression);
      if (strippedExpression == null) {
        return false;
      }
      if (!(strippedExpression instanceof PsiAssignmentExpression)) {
        return false;
      }
      PsiAssignmentExpression assignment =
        (PsiAssignmentExpression)strippedExpression;
      IElementType tokenType = assignment.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.EQ)) {
        return false;
      }
      PsiExpression lhs = assignment.getLExpression();
      if (SideEffectChecker.mayHaveSideEffects(lhs)) {
        return false;
      }
      if (!ExpressionUtils.isOffsetArrayAccess(lhs, variable)) {
        return false;
      }
      PsiExpression rhs = assignment.getRExpression();
      if (rhs == null) {
        return false;
      }
      if (SideEffectChecker.mayHaveSideEffects(rhs)) {
        return false;
      }
      if (!areExpressionsCopyable(lhs, rhs)) {
        return false;
      }
      PsiType type = lhs.getType();
      if (type instanceof PsiPrimitiveType) {
        PsiExpression strippedLhs =
          ParenthesesUtils.stripParentheses(lhs);
        PsiExpression strippedRhs =
          ParenthesesUtils.stripParentheses(rhs);
        if (!areExpressionsCopyable(strippedLhs, strippedRhs)) {
          return false;
        }
      }
      if (variable2 == null) {
        return ExpressionUtils.isOffsetArrayAccess(rhs, variable);
      }
      else {
        return VariableAccessUtils.evaluatesToVariable(rhs, variable2);
      }
    }

    private static boolean areExpressionsCopyable(
      @Nullable PsiExpression lhs, @Nullable PsiExpression rhs) {
      if (lhs == null || rhs == null) {
        return false;
      }
      PsiType lhsType = lhs.getType();
      if (lhsType == null) {
        return false;
      }
      PsiType rhsType = rhs.getType();
      if (rhsType == null) {
        return false;
      }
      if (lhsType instanceof PsiPrimitiveType) {
        if (!lhsType.equals(rhsType)) {
          return false;
        }
      }
      else {
        if (!lhsType.isAssignableFrom(rhsType) ||
            rhsType instanceof PsiPrimitiveType) {
          return false;
        }
      }
      return true;
    }
  }
}