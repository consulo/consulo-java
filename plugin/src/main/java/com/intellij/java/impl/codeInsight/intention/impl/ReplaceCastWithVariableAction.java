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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressIndicatorProvider;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Danila Ponomarenko
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceCastWithVariableAction", categories = {"Java", "Other"}, fileExtensions = "java")
public class ReplaceCastWithVariableAction extends PsiElementBaseIntentionAction {
  private String myReplaceVariableName = "";

  public ReplaceCastWithVariableAction() {
    setText(CodeInsightLocalize.intentionReplaceCastWithVarFamily());
  }

  @RequiredReadAction
  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    PsiTypeCastExpression typeCastExpression = PsiTreeUtil.getParentOfType(element, PsiTypeCastExpression.class);
    PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

    if (typeCastExpression == null || method == null) {
      return false;
    }

    PsiExpression operand = typeCastExpression.getOperand();
    if (!(operand instanceof PsiReferenceExpression)) {
      return false;
    }

    PsiReferenceExpression operandReference = (PsiReferenceExpression)operand;
    PsiElement resolved = operandReference.resolve();
    if (resolved == null || (!(resolved instanceof PsiParameter) && !(resolved instanceof PsiLocalVariable))) {
      return false;
    }

    PsiLocalVariable replacement = findReplacement(method, (PsiVariable)resolved, typeCastExpression);
    if (replacement == null) {
      return false;
    }

    myReplaceVariableName = replacement.getName();
    setText(CodeInsightLocalize.intentionReplaceCastWithVarText(typeCastExpression.getText(), myReplaceVariableName));

    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    PsiTypeCastExpression typeCastExpression = PsiTreeUtil.getParentOfType(element, PsiTypeCastExpression.class);

    if (typeCastExpression == null) {
      return;
    }

    PsiElement toReplace = typeCastExpression.getParent() instanceof PsiParenthesizedExpression parenthesizedExpression
      ? parenthesizedExpression : typeCastExpression;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    toReplace.replace(factory.createExpressionFromText(myReplaceVariableName, toReplace));
  }

  @Nullable
  @RequiredReadAction
  private static PsiLocalVariable findReplacement(
    @Nonnull PsiMethod method,
    @Nonnull PsiVariable castedVar,
    @Nonnull PsiTypeCastExpression expression
  ) {
    TextRange expressionTextRange = expression.getTextRange();
    for (PsiExpression occurrence : CodeInsightUtil.findExpressionOccurrences(method,expression)){
      ProgressIndicatorProvider.checkCanceled();
      TextRange occurrenceTextRange = occurrence.getTextRange();
      if (occurrence == expression || occurrenceTextRange.getEndOffset() >= expressionTextRange.getStartOffset()) {
        continue;
      }

      PsiLocalVariable variable = getVariable(occurrence);

      PsiCodeBlock methodBody = method.getBody();
      if (variable != null && methodBody != null &&
          !isChangedBetween(castedVar, methodBody, occurrence, expression) && !isChangedBetween(variable, methodBody, occurrence, expression)) {
        return variable;
      }
    }


    return null;
  }

  private static boolean isChangedBetween(
    @Nonnull final PsiVariable variable,
    @Nonnull PsiElement scope,
    @Nonnull final PsiElement start,
    @Nonnull final PsiElement end
  ) {
    if (variable.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }

    final Ref<Boolean> result = new Ref<>();

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
          if (inScope && expression.getLExpression() instanceof PsiReferenceExpression referenceExpression) {
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

  @Nullable
  private static PsiLocalVariable getVariable(@Nonnull PsiExpression occurrence) {
    PsiElement parent = occurrence.getParent();

    if (parent instanceof PsiLocalVariable localVariable) {
      return localVariable;
    }

    if (parent instanceof PsiAssignmentExpression assignmentExpression) {
      if (assignmentExpression.getLExpression() instanceof PsiReferenceExpression) {
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression)assignmentExpression.getLExpression();
        PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof PsiLocalVariable localVariable) {
          return localVariable;
        }
      }
    }

    return null;
  }
}
