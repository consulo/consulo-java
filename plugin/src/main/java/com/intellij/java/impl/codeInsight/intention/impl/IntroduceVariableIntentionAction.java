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

import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionStatement;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.refactoring.action.BaseRefactoringIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SyntheticElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

/**
 * @author Danila Ponomarenko
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.IntroduceVariableIntentionAction", categories = {"Java", "Refactorings"}, fileExtensions = "java")
public class IntroduceVariableIntentionAction extends BaseRefactoringIntentionAction {
  @Nonnull
  @Override
  public String getText() {
    return CodeInsightBundle.message("intention.introduce.variable.text");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @jakarta.annotation.Nonnull PsiElement element) {
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
