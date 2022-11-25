
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
package com.intellij.java.impl.codeInsight.generation.surroundWith;

import consulo.language.editor.CodeInsightBundle;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiParenthesizedExpression;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.*;
import consulo.language.codeStyle.CodeStyleManager;
import com.intellij.java.impl.refactoring.introduceVariable.IntroduceVariableBase;
import consulo.language.util.IncorrectOperationException;

class JavaWithParenthesesSurrounder extends JavaExpressionSurrounder{
  @Override
  public boolean isApplicable(PsiExpression expr) {
    return true;
  }

  @Override
  public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
    PsiManager manager = expr.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    PsiParenthesizedExpression parenthExpr = (PsiParenthesizedExpression)factory.createExpressionFromText("(a)", null);
    parenthExpr = (PsiParenthesizedExpression)codeStyleManager.reformat(parenthExpr);
    parenthExpr.getExpression().replace(expr);
    expr = (PsiExpression)IntroduceVariableBase.replace(expr, parenthExpr, project);
    int offset = expr.getTextRange().getEndOffset();
    return new TextRange(offset, offset);
  }

  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.parenthesis.template");
  }
}