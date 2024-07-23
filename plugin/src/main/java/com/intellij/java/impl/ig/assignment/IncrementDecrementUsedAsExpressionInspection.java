/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.assignment;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.scope.SearchScope;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class IncrementDecrementUsedAsExpressionInspection
  extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "ValueOfIncrementOrDecrementUsed";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.incrementDecrementDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final Object info = infos[0];
    if (info instanceof PsiPostfixExpression) {
      final PsiPostfixExpression postfixExpression =
        (PsiPostfixExpression)info;
      final IElementType tokenType =
        postfixExpression.getOperationTokenType();
      return JavaTokenType.PLUSPLUS.equals(tokenType)
        ? InspectionGadgetsLocalize.valueOfPostIncrementProblemDescriptor().get()
        : InspectionGadgetsLocalize.valueOfPostDecrementProblemDescriptor().get();
    }
    else {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)info;
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      return JavaTokenType.PLUSPLUS.equals(tokenType)
        ? InspectionGadgetsLocalize.valueOfPreIncrementProblemDescriptor().get()
        : InspectionGadgetsLocalize.valueOfPreDecrementProblemDescriptor().get();
    }
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    return new IncrementDecrementUsedAsExpressionFix(expression.getText());
  }

  private static class IncrementDecrementUsedAsExpressionFix
    extends InspectionGadgetsFix {

    private final String elementText;

    IncrementDecrementUsedAsExpressionFix(String elementText) {
      this.elementText = elementText;
    }

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.incrementDecrementUsedAsExpressionQuickfix(elementText).get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      // see also the Extract Increment intention of IPP
      final PsiElement element = descriptor.getPsiElement();
      final PsiExpression operand;
      if (element instanceof PsiPostfixExpression) {
        final PsiPostfixExpression postfixExpression =
          (PsiPostfixExpression)element;
        operand = postfixExpression.getOperand();
      }
      else {
        final PsiPrefixExpression prefixExpression =
          (PsiPrefixExpression)element;
        operand = prefixExpression.getOperand();
      }
      if (operand == null) {
        return;
      }
      final PsiStatement statement =
        PsiTreeUtil.getParentOfType(element, PsiStatement.class);
      if (statement == null) {
        return;
      }
      final PsiElement parent = statement.getParent();
      if (parent == null) {
        return;
      }
      final PsiElementFactory factory =
        JavaPsiFacade.getInstance(project).getElementFactory();
      final String newStatementText = element.getText() + ';';
      final String operandText = operand.getText();
      if (parent instanceof PsiIfStatement ||
          parent instanceof PsiLoopStatement) {
        // need to add braces because
        // in/decrement is inside braceless control statement body
        final StringBuilder text = new StringBuilder();
        text.append('{');
        final String elementText =
          getElementText(statement, element, operandText);
        if (element instanceof PsiPostfixExpression) {
          text.append(elementText);
          text.append(newStatementText);
        }
        else {
          text.append(newStatementText);
          text.append(elementText);
        }
        text.append('}');
        final PsiCodeBlock codeBlock =
          factory.createCodeBlockFromText(text.toString(), parent);
        statement.replace(codeBlock);
        return;
      }
      final PsiStatement newStatement =
        factory.createStatementFromText(newStatementText, element);
      if (statement instanceof PsiReturnStatement) {
        if (element instanceof PsiPostfixExpression) {
          // special handling of postfix expression in return statement
          final PsiReturnStatement returnStatement =
            (PsiReturnStatement)statement;
          final PsiExpression returnValue =
            returnStatement.getReturnValue();
          if (returnValue == null) {
            return;
          }
          final JavaCodeStyleManager javaCodeStyleManager =
            JavaCodeStyleManager.getInstance(project);
          final String variableName =
            javaCodeStyleManager.suggestUniqueVariableName(
              "result", returnValue, true);
          final PsiType type = returnValue.getType();
          if (type == null) {
            return;
          }
          final String newReturnValueText = getElementText(
            returnValue, element, operandText);
          final String declarationStatementText =
            type.getCanonicalText() + ' ' + variableName +
            '=' + newReturnValueText + ';';
          final PsiStatement declarationStatement =
            factory.createStatementFromText(declarationStatementText,
                                            returnStatement);
          parent.addBefore(declarationStatement, statement);
          parent.addBefore(newStatement, statement);
          final PsiStatement newReturnStatement =
            factory.createStatementFromText(
              "return " + variableName + ';',
              returnStatement);
          returnStatement.replace(newReturnStatement);
          return;
        }
        else {
          parent.addBefore(newStatement, statement);
        }
      }
      else if (statement instanceof PsiThrowStatement) {
        if (element instanceof PsiPostfixExpression) {
          // special handling of postfix expression in throw statement
          final PsiThrowStatement returnStatement =
            (PsiThrowStatement)statement;
          final PsiExpression exception =
            returnStatement.getException();
          if (exception == null) {
            return;
          }
          final JavaCodeStyleManager javaCodeStyleManager =
            JavaCodeStyleManager.getInstance(project);
          final String variableName =
            javaCodeStyleManager.suggestUniqueVariableName(
              "e", exception, true);
          final PsiType type = exception.getType();
          if (type == null) {
            return;
          }
          final String newReturnValueText = getElementText(
            exception, element, operandText);
          final String declarationStatementText =
            type.getCanonicalText() + ' ' + variableName +
            '=' + newReturnValueText + ';';
          final PsiStatement declarationStatement =
            factory.createStatementFromText(declarationStatementText,
                                            returnStatement);
          parent.addBefore(declarationStatement, statement);
          parent.addBefore(newStatement, statement);
          final PsiStatement newReturnStatement =
            factory.createStatementFromText(
              "throw " + variableName + ';',
              returnStatement);
          returnStatement.replace(newReturnStatement);
          return;
        }
        else {
          parent.addBefore(newStatement, statement);
        }
      }
      else if (!(statement instanceof PsiForStatement)) {
        if (element instanceof PsiPostfixExpression) {
          parent.addAfter(newStatement, statement);
        }
        else {
          parent.addBefore(newStatement, statement);
        }
      }
      else if (operand instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)operand;
        final PsiElement target = referenceExpression.resolve();
        if (target != null) {
          final SearchScope useScope = target.getUseScope();
          if (!new LocalSearchScope(statement).equals(useScope)) {
            if (element instanceof PsiPostfixExpression) {
              parent.addAfter(newStatement, statement);
            }
            else {
              parent.addBefore(newStatement, statement);
            }
          }
        }
      }
      if (statement instanceof PsiLoopStatement) {
        // in/decrement inside loop statement condition
        final PsiLoopStatement loopStatement =
          (PsiLoopStatement)statement;
        final PsiStatement body = loopStatement.getBody();
        if (body instanceof PsiBlockStatement) {
          final PsiBlockStatement blockStatement =
            (PsiBlockStatement)body;
          final PsiCodeBlock codeBlock =
            blockStatement.getCodeBlock();
          if (element instanceof PsiPostfixExpression) {
            final PsiElement firstElement =
              codeBlock.getFirstBodyElement();
            codeBlock.addBefore(newStatement, firstElement);
          }
          else {
            codeBlock.add(newStatement);
          }
        }
        else {
          final StringBuilder blockText = new StringBuilder();
          blockText.append('{');
          if (element instanceof PsiPostfixExpression) {
            blockText.append(newStatementText);
            if (body != null) {
              blockText.append(body.getText());
            }
          }
          else {
            if (body != null) {
              blockText.append(body.getText());
            }
            blockText.append(newStatementText);
          }
          blockText.append('}');
          final PsiStatement blockStatement =
            factory.createStatementFromText(
              blockText.toString(), statement);
          if (body == null) {
            loopStatement.add(blockStatement);
          }
          else {
            body.replace(blockStatement);
          }
        }
      }
      replaceExpression((PsiExpression)element, operandText);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IncrementDecrementUsedAsExpressionVisitor();
  }

  private static class IncrementDecrementUsedAsExpressionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitPostfixExpression(
      @Nonnull PsiPostfixExpression expression) {
      super.visitPostfixExpression(expression);
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiExpressionStatement ||
          (parent instanceof PsiExpressionList &&
           parent.getParent() instanceof
             PsiExpressionListStatement)) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
          !tokenType.equals(JavaTokenType.MINUSMINUS)) {
        return;
      }
      registerError(expression, expression);
    }

    @Override
    public void visitPrefixExpression(
      @Nonnull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiExpressionStatement ||
          (parent instanceof PsiExpressionList &&
           parent.getParent() instanceof
             PsiExpressionListStatement)) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
          !tokenType.equals(JavaTokenType.MINUSMINUS)) {
        return;
      }
      registerError(expression, expression);
    }
  }
}