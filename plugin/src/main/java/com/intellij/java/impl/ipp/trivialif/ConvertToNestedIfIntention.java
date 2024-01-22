/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.ipp.trivialif;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

/**
 * @author anna
 * Date: 2/2/12
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertToNestedIfIntention", fileExtensions = "java", categories = {"Java", "Boolean"})
public class ConvertToNestedIfIntention extends Intention {

  @Override
  @jakarta.annotation.Nonnull
  public PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {

      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof PsiReturnStatement)) {
          return false;
        }
        final PsiReturnStatement returnStatement = (PsiReturnStatement)element;
        final PsiExpression returnValue = ParenthesesUtils.stripParentheses(returnStatement.getReturnValue());
        if (!(returnValue instanceof PsiPolyadicExpression)) {
          return false;
        }
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)returnValue;
        final IElementType tokenType = polyadicExpression.getOperationTokenType();
        return tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR;
      }
    };
  }

  @Override
  public void processIntention(@Nonnull PsiElement element) {
    final PsiReturnStatement returnStatement = (PsiReturnStatement)element;
    final PsiExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null || ErrorUtil.containsDeepError(returnValue)) {
      return;
    }
    final String newStatementText = buildIf(returnValue, new StringBuilder()).toString();
    final Project project = returnStatement.getProject();
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    final PsiBlockStatement blockStatement = (PsiBlockStatement)elementFactory.createStatementFromText("{" + newStatementText + "}", returnStatement);
    final PsiElement parent = returnStatement.getParent();
    for (PsiStatement st : blockStatement.getCodeBlock().getStatements()) {
      CodeStyleManager.getInstance(project).reformat(parent.addBefore(st, returnStatement));
    }
    replaceStatement("return false;", returnStatement);
  }

  private static StringBuilder buildIf(@Nullable PsiExpression expression, StringBuilder out) {
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.ANDAND.equals(tokenType)) {
        for (PsiExpression operand : operands) {
          buildIf(operand, out);
        }
        if (!StringUtil.endsWith(out, "return true;")) {
          out.append("return true;");
        }
        return out;
      }
      else if (JavaTokenType.OROR.equals(tokenType)) {
        for (PsiExpression operand : operands) {
          buildIf(operand, out);
          if (!StringUtil.endsWith(out, "return true;")) {
            out.append("return true;");
          }
        }
        return out;
      }
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      buildIf(parenthesizedExpression.getExpression(), out);
      return out;
    }
    if (expression != null) {
      out.append("if(").append(expression.getText()).append(")");
    }
    return out;
  }
}
