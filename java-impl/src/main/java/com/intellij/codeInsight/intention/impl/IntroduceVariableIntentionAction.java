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
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionStatement;
import com.intellij.java.language.psi.PsiType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringIntentionAction;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Danila Ponomarenko
 */
public class IntroduceVariableIntentionAction extends BaseRefactoringIntentionAction {
  @Nonnull
  @Override
  public String getText() {
    return CodeInsightBundle.message("intention.introduce.variable.text");
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    if (element instanceof SyntheticElement){
      return false;
    }

    final PsiExpressionStatement statement = PsiTreeUtil.getParentOfType(element,PsiExpressionStatement.class);
    if (statement == null){
      return false;
    }

    final PsiExpression expression = statement.getExpression();

    return !PsiType.VOID.equals(expression.getType()) && !(expression instanceof PsiAssignmentExpression);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    final PsiExpressionStatement statement = PsiTreeUtil.getParentOfType(element,PsiExpressionStatement.class);
    if (statement == null){
      return;
    }

    new IntroduceVariableHandler().invoke(project, editor, statement.getExpression());
  }
}
