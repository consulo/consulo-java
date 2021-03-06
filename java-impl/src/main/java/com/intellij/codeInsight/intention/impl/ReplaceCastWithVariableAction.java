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
package com.intellij.codeInsight.intention.impl;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Danila Ponomarenko
 */
public class ReplaceCastWithVariableAction extends PsiElementBaseIntentionAction {
  private String myReplaceVariableName = "";

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    final PsiTypeCastExpression typeCastExpression = PsiTreeUtil.getParentOfType(element, PsiTypeCastExpression.class);
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

    if (typeCastExpression == null || method == null) {
      return false;
    }

    final PsiExpression operand = typeCastExpression.getOperand();
    if (!(operand instanceof PsiReferenceExpression)) {
      return false;
    }

    final PsiReferenceExpression operandReference = (PsiReferenceExpression)operand;
    final PsiElement resolved = operandReference.resolve();
    if (resolved == null || (!(resolved instanceof PsiParameter) && !(resolved instanceof PsiLocalVariable))) {
      return false;
    }

    final PsiLocalVariable replacement = findReplacement(method, (PsiVariable)resolved, typeCastExpression);
    if (replacement == null) {
      return false;
    }

    myReplaceVariableName = replacement.getName();
    setText(CodeInsightBundle.message("intention.replace.cast.with.var.text", typeCastExpression.getText(), myReplaceVariableName));

    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    final PsiTypeCastExpression typeCastExpression = PsiTreeUtil.getParentOfType(element, PsiTypeCastExpression.class);

    if (typeCastExpression == null) {
      return;
    }

    final PsiElement toReplace = typeCastExpression.getParent() instanceof PsiParenthesizedExpression ? typeCastExpression.getParent() : typeCastExpression;
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    toReplace.replace(factory.createExpressionFromText(myReplaceVariableName, toReplace));
  }

  @javax.annotation.Nullable
  private static PsiLocalVariable findReplacement(@Nonnull PsiMethod method,
                                                  @Nonnull PsiVariable castedVar,
                                                  @Nonnull PsiTypeCastExpression expression) {
    final TextRange expressionTextRange = expression.getTextRange();
    for (PsiExpression occurrence : CodeInsightUtil.findExpressionOccurrences(method,expression)){
      ProgressIndicatorProvider.checkCanceled();
      final TextRange occurrenceTextRange = occurrence.getTextRange();
      if (occurrence == expression || occurrenceTextRange.getEndOffset() >= expressionTextRange.getStartOffset()) {
        continue;
      }

      final PsiLocalVariable variable = getVariable(occurrence);

      final PsiCodeBlock methodBody = method.getBody();
      if (variable != null && methodBody != null &&
          !isChangedBetween(castedVar, methodBody, occurrence, expression) && !isChangedBetween(variable, methodBody, occurrence, expression)) {
        return variable;
      }
    }


    return null;
  }

  private static boolean isChangedBetween(@Nonnull final PsiVariable variable,
                                          @Nonnull final PsiElement scope,
                                          @Nonnull final PsiElement start,
                                          @Nonnull final PsiElement end) {
    if (variable.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }

    final Ref<Boolean> result = new Ref<Boolean>();

    scope.accept(
      new JavaRecursiveElementWalkingVisitor() {
        private boolean inScope = false;

        @Override
        public void visitElement(PsiElement element) {
          if (element == start) {
            inScope = true;
          }
          if (element == end) {
            inScope = false;
            stopWalking();
          }
          super.visitElement(element);
        }

        @Override
        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
          if (inScope && expression.getLExpression() instanceof PsiReferenceExpression) {
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression.getLExpression();

            if (variable.equals(referenceExpression.resolve())) {
              result.set(true);
              stopWalking();
            }
          }
          super.visitAssignmentExpression(expression);
        }
      }
    );
    return result.get() == Boolean.TRUE;
  }

  @javax.annotation.Nullable
  private static PsiLocalVariable getVariable(@Nonnull PsiExpression occurrence) {
    final PsiElement parent = occurrence.getParent();

    if (parent instanceof PsiLocalVariable) {
      return (PsiLocalVariable)parent;
    }

    if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      if (assignmentExpression.getLExpression() instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)assignmentExpression.getLExpression();
        final PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof PsiLocalVariable) {
          return (PsiLocalVariable)resolved;
        }
      }
    }

    return null;
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.replace.cast.with.var.family");
  }
}
