
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

class JavaWithForSurrounder extends JavaStatementsSurrounder{
  @Override
  public LocalizeValue getTemplateDescription() {
    return CodeInsightLocalize.surroundWithForTemplate();
  }

  @Override
  @RequiredReadAction
  public TextRange surroundStatements(Project project, Editor editor, PsiElement container, PsiElement[] statements) throws IncorrectOperationException{
    PsiManager manager = PsiManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, true);
    if (statements.length == 0){
      return null;
    }

    @NonNls String text = "for(a;b;c){\n}";
    PsiForStatement forStatement = (PsiForStatement)factory.createStatementFromText(text, null);
    forStatement = (PsiForStatement)codeStyleManager.reformat(forStatement);

    forStatement = (PsiForStatement)container.addAfter(forStatement, statements[statements.length - 1]);

    PsiStatement body = forStatement.getBody();
    if (!(body instanceof PsiBlockStatement)) {
      return null;
    }
    PsiCodeBlock bodyBlock = ((PsiBlockStatement)body).getCodeBlock();
    SurroundWithUtil.indentCommentIfNecessary(bodyBlock, statements);
    bodyBlock.addRange(statements[0], statements[statements.length - 1]);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    forStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(forStatement);
    PsiStatement initialization = forStatement.getInitialization();
    if (initialization == null) {
      return null;
    }
    TextRange range1 = initialization.getTextRange();
    
    PsiStatement update = forStatement.getUpdate();
    if (update == null) {
      return null;
    }
    TextRange range3 = update.getTextRange();
    editor.getDocument().deleteString(range1.getStartOffset(), range3.getEndOffset());
    return new TextRange(range1.getStartOffset(), range1.getStartOffset());
  }
}