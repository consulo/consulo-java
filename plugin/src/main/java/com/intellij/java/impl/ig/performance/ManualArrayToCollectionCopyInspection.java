/*
 * Copyright 2006-2011 Bas Leijdekkers
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
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
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
public class ManualArrayToCollectionCopyInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.manualArrayToCollectionCopyDisplayName();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.manualArrayToCollectionCopyProblemDescriptor().get();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ManualArrayToCollectionCopyFix();
  }

  private static class ManualArrayToCollectionCopyFix
    extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.manualArrayToCollectionCopyReplaceQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      PsiElement forElement = descriptor.getPsiElement();
      PsiElement parent = forElement.getParent();
      String newExpression;
      if (parent instanceof PsiForStatement) {
        PsiForStatement forStatement =
          (PsiForStatement)parent;
        newExpression = getCollectionsAddAllText(forStatement);
        if (newExpression == null) {
          return;
        }
        replaceStatementAndShortenClassNames(forStatement,
                                             newExpression);
      }
      else {
        PsiForeachStatement foreachStatement =
          (PsiForeachStatement)parent;
        newExpression = getCollectionsAddAllText(foreachStatement);
        if (newExpression == null) {
          return;
        }
        replaceStatementAndShortenClassNames(foreachStatement,
                                             newExpression);
      }
    }

    @Nullable
    private static String getCollectionsAddAllText(
      PsiForeachStatement foreachStatement)
      throws IncorrectOperationException {
      PsiStatement body = getBody(foreachStatement);
      if (!(body instanceof PsiExpressionStatement)) {
        return null;
      }
      PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement)body;
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)expressionStatement.getExpression();
      PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      PsiElement collection = methodExpression.getQualifier();
      if (collection == null) {
        // fixme for when the array is added to 'this'
        return null;
      }
      String collectionText = collection.getText();
      PsiExpression iteratedValue =
        foreachStatement.getIteratedValue();
      if (iteratedValue == null) {
        return null;
      }
      String arrayText = iteratedValue.getText();
      @NonNls StringBuilder buffer = new StringBuilder();
      if (PsiUtil.isLanguageLevel5OrHigher(foreachStatement)) {
        buffer.append("java.util.Collections.addAll(");
        buffer.append(collectionText);
        buffer.append(',');
        buffer.append(arrayText);
        buffer.append(");");
      }
      else {
        buffer.append(collectionText);
        buffer.append(".addAll(java.util.Arrays.asList(");
        buffer.append(arrayText);
        buffer.append("));");
      }
      return buffer.toString();
    }

    @Nullable
    private static String getCollectionsAddAllText(
      PsiForStatement forStatement)
      throws IncorrectOperationException {
      PsiExpression expression = forStatement.getCondition();
      PsiBinaryExpression condition =
        (PsiBinaryExpression)ParenthesesUtils.stripParentheses(
          expression);
      if (condition == null) {
        return null;
      }
      PsiStatement initialization =
        forStatement.getInitialization();
      if (initialization == null) {
        return null;
      }
      if (!(initialization instanceof PsiDeclarationStatement)) {
        return null;
      }
      PsiDeclarationStatement declaration =
        (PsiDeclarationStatement)initialization;
      PsiElement[] declaredElements =
        declaration.getDeclaredElements();
      if (declaredElements.length != 1) {
        return null;
      }
      PsiElement declaredElement = declaredElements[0];
      if (!(declaredElement instanceof PsiLocalVariable)) {
        return null;
      }
      PsiLocalVariable variable = (PsiLocalVariable)declaredElement;
      String collectionText = buildCollectionText(forStatement);
      PsiArrayAccessExpression arrayAccessExpression =
        getArrayAccessExpression(forStatement);
      if (arrayAccessExpression == null) {
        return null;
      }
      PsiExpression arrayExpression =
        arrayAccessExpression.getArrayExpression();
      String arrayText = arrayExpression.getText();
      PsiExpression indexExpression =
        arrayAccessExpression.getIndexExpression();
      String fromOffsetText =
        buildFromOffsetText(indexExpression, variable);
      if (fromOffsetText == null) {
        return null;
      }
      PsiExpression limit;
      IElementType tokenType = condition.getOperationTokenType();
      if (tokenType == JavaTokenType.LT ||
          tokenType == JavaTokenType.LE) {
        limit = condition.getROperand();
      }
      else {
        limit = condition.getLOperand();
      }
      @NonNls String toOffsetText =
        buildToOffsetText(limit, tokenType == JavaTokenType.LE ||
                                 tokenType == JavaTokenType.GE);
      if (toOffsetText == null) {
        return null;
      }
      if (fromOffsetText.equals("0") &&
          toOffsetText.equals(arrayText + ".length") &&
          PsiUtil.isLanguageLevel5OrHigher(forStatement)) {
        @NonNls StringBuilder buffer =
          new StringBuilder("java.util.Collections.addAll(");
        buffer.append(collectionText);
        buffer.append(',');
        buffer.append(arrayText);
        buffer.append(");");
        return buffer.toString();
      }
      else {
        @NonNls StringBuilder buffer = new StringBuilder();
        buffer.append(collectionText);
        buffer.append('.');
        buffer.append("addAll(java.util.Arrays.asList(");
        buffer.append(arrayText);
        buffer.append(')');
        if (!fromOffsetText.equals("0") ||
            !toOffsetText.equals(arrayText + ".length")) {
          buffer.append(".subList(");
          buffer.append(fromOffsetText);
          buffer.append(", ");
          buffer.append(toOffsetText);
          buffer.append(')');
        }
        buffer.append(");");
        return buffer.toString();
      }
    }

    private static PsiArrayAccessExpression getArrayAccessExpression(
      PsiForStatement forStatement) {
      PsiStatement body = getBody(forStatement);
      if (body == null) {
        return null;
      }
      PsiExpression arrayAccessExpression;
      if (body instanceof PsiExpressionStatement) {
        PsiExpressionStatement expressionStatement =
          (PsiExpressionStatement)body;
        PsiExpression expression =
          expressionStatement.getExpression();
        if (!(expression instanceof PsiMethodCallExpression)) {
          return null;
        }
        PsiMethodCallExpression methodCallExpression =
          (PsiMethodCallExpression)expression;
        PsiExpressionList argumentList =
          methodCallExpression.getArgumentList();
        arrayAccessExpression = argumentList.getExpressions()[0];
      }
      else if (body instanceof PsiDeclarationStatement) {
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
      else {
        return null;
      }
      PsiExpression deparenthesizedArgument =
        ParenthesesUtils.stripParentheses(arrayAccessExpression);
      if (!(deparenthesizedArgument instanceof
              PsiArrayAccessExpression)) {
        return null;
      }
      return (PsiArrayAccessExpression)deparenthesizedArgument;
    }

    public static String buildCollectionText(PsiForStatement forStatement) {
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
      if (!(expression instanceof PsiMethodCallExpression)) {
        return null;
      }
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)expression;
      PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      PsiElement qualifier = methodExpression.getQualifier();
      if (qualifier == null) {
        // fixme for when the array is added to 'this'
        return null;
      }
      return qualifier.getText();
    }

    @Nullable
    private static String buildFromOffsetText(PsiExpression expression,
                                              PsiLocalVariable variable)
      throws IncorrectOperationException {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (expression == null) {
        return null;
      }
      String expressionText = expression.getText();
      String variableName = variable.getName();
      if (expressionText.equals(variableName)) {
        PsiExpression initialValue = variable.getInitializer();
        if (initialValue == null) {
          return null;
        }
        return initialValue.getText();
      }
      if (expression instanceof PsiBinaryExpression) {
        PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)expression;
        PsiExpression lhs = binaryExpression.getLOperand();
        PsiExpression rhs = binaryExpression.getROperand();
        String rhsText = buildFromOffsetText(rhs, variable);
        PsiJavaToken sign = binaryExpression.getOperationSign();
        IElementType tokenType = sign.getTokenType();
        if (ExpressionUtils.isZero(lhs)) {
          if (tokenType.equals(JavaTokenType.MINUS)) {
            return '-' + rhsText;
          }
          return rhsText;
        }
        String lhsText = buildFromOffsetText(lhs, variable);
        if (ExpressionUtils.isZero(rhs)) {
          return lhsText;
        }
        return collapseConstant(lhsText + sign.getText() + rhsText,
                                variable);
      }
      return collapseConstant(expression.getText(), variable);
    }

    private static String buildToOffsetText(PsiExpression expression,
                                            boolean plusOne) {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (expression == null) {
        return null;
      }
      if (!plusOne) {
        return expression.getText();
      }
      if (expression instanceof PsiBinaryExpression) {
        PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)expression;
        IElementType tokenType =
          binaryExpression.getOperationTokenType();
        if (tokenType == JavaTokenType.MINUS) {
          PsiExpression rhs =
            binaryExpression.getROperand();
          if (ExpressionUtils.isOne(rhs)) {
            return binaryExpression.getLOperand().getText();
          }
        }
      }
      int precedence = ParenthesesUtils.getPrecedence(expression);
      if (precedence > ParenthesesUtils.ADDITIVE_PRECEDENCE) {
        return '(' + expression.getText() + ")+1";
      }
      else {
        return expression.getText() + "+1";
      }
    }

    private static String collapseConstant(String expressionText,
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

    @Nullable
    private static PsiStatement getBody(PsiLoopStatement forStatement) {
      PsiStatement body = forStatement.getBody();
      while (body instanceof PsiBlockStatement) {
        PsiBlockStatement blockStatement =
          (PsiBlockStatement)body;
        PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        PsiStatement[] statements = codeBlock.getStatements();
        body = statements[0];
      }
      return body;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ManualArrayToCollectionCopyVisitor();
  }

  private static class ManualArrayToCollectionCopyVisitor
    extends BaseInspectionVisitor {

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
      PsiExpression condition = statement.getCondition();
      if (!ExpressionUtils.isVariableLessThanComparison(condition,
                                                        variable)) {
        return;
      }
      PsiStatement update = statement.getUpdate();
      if (!VariableAccessUtils.variableIsIncremented(variable, update)) {
        return;
      }
      PsiStatement body = statement.getBody();
      if (!bodyIsArrayToCollectionCopy(body, variable, true)) {
        return;
      }
      registerStatementError(statement);
    }

    @Override
    public void visitForeachStatement(
      PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      PsiExpression iteratedValue = statement.getIteratedValue();
      if (iteratedValue == null) {
        return;
      }
      PsiType type = iteratedValue.getType();
      if (!(type instanceof PsiArrayType)) {
        return;
      }
      PsiArrayType arrayType = (PsiArrayType)type;
      PsiType componentType = arrayType.getComponentType();
      if (componentType instanceof PsiPrimitiveType) {
        return;
      }
      PsiParameter parameter = statement.getIterationParameter();
      PsiStatement body = statement.getBody();
      if (!bodyIsArrayToCollectionCopy(body, parameter, false)) {
        return;
      }
      registerStatementError(statement);
    }

    private static boolean bodyIsArrayToCollectionCopy(
      PsiStatement body, PsiVariable variable,
      boolean shouldBeOffsetArrayAccess) {
      if (body instanceof PsiExpressionStatement) {
        PsiExpressionStatement expressionStatement =
          (PsiExpressionStatement)body;
        PsiExpression expression =
          expressionStatement.getExpression();
        return expressionIsArrayToCollectionCopy(expression, variable,
                                                 shouldBeOffsetArrayAccess);
      }
      else if (body instanceof PsiBlockStatement) {
        PsiBlockStatement blockStatement =
          (PsiBlockStatement)body;
        PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 1) {
          return bodyIsArrayToCollectionCopy(statements[0], variable,
                                             shouldBeOffsetArrayAccess);
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
          return bodyIsArrayToCollectionCopy(statements[1],
                                             localVariable, false);
        }
      }
      return false;
    }

    private static boolean expressionIsArrayToCollectionCopy(
      PsiExpression expression, PsiVariable variable,
      boolean shouldBeOffsetArrayAccess) {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (expression == null) {
        return false;
      }
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)expression;
      PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return false;
      }
      PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression) &&
          !(qualifier instanceof PsiThisExpression) &&
          !(qualifier instanceof PsiSuperExpression)) {
        return false;
      }
      PsiExpression argument = arguments[0];
      PsiType argumentType = argument.getType();
      if (argumentType instanceof PsiPrimitiveType) {
        return false;
      }
      if (SideEffectChecker.mayHaveSideEffects(argument)) {
        return false;
      }
      if (shouldBeOffsetArrayAccess) {
        if (!ExpressionUtils.isOffsetArrayAccess(argument, variable)) {
          return false;
        }
      }
      else if (!VariableAccessUtils.evaluatesToVariable(argument,
                                                        variable)) {
        return false;
      }
      PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      @NonNls String name = method.getName();
      if (!name.equals("add")) {
        return false;
      }
      PsiClass containingClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_COLLECTION);
    }
  }
}