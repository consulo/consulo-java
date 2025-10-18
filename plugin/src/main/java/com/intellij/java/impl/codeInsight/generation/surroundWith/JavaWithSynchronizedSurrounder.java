
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

import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

class JavaWithSynchronizedSurrounder extends JavaStatementsSurrounder{
  @Override
  public LocalizeValue getTemplateDescription() {
    return CodeInsightLocalize.surroundWithSynchronizedTemplate();
  }

  @Override
  @RequiredReadAction
  public TextRange surroundStatements(Project project, Editor editor, PsiElement container, PsiElement[] statements)
    throws IncorrectOperationException {
    PsiManager manager = PsiManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, false);
    if (statements.length == 0){
      return null;
    }

    @NonNls String text = "synchronized(a){\n}";
    PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement)factory.createStatementFromText(text, null);
    synchronizedStatement = (PsiSynchronizedStatement)codeStyleManager.reformat(synchronizedStatement);

    synchronizedStatement = (PsiSynchronizedStatement)container.addAfter(synchronizedStatement, statements[statements.length - 1]);

    PsiCodeBlock synchronizedBlock = synchronizedStatement.getBody();
    if (synchronizedBlock == null) {
      return null;
    }
    SurroundWithUtil.indentCommentIfNecessary(synchronizedBlock, statements);
    synchronizedBlock.addRange(statements[0], statements[statements.length - 1]);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    synchronizedStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(synchronizedStatement);
    PsiExpression lockExpression = synchronizedStatement.getLockExpression();
    if (lockExpression == null) {
      return null;
    }
    TextRange range = lockExpression.getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    return new TextRange(range.getStartOffset(), range.getStartOffset());
  }
}