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

import com.intellij.java.language.impl.codeInsight.ExceptionUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;
import java.util.List;

public class JavaWithTryCatchSurrounder extends JavaStatementsSurrounder {
  protected boolean myGenerateFinally = false;

  @Override
  public LocalizeValue getTemplateDescription() {
    return CodeInsightLocalize.surroundWithTryCatchTemplate();
  }

  @Override
  public TextRange surroundStatements(Project project, Editor editor, PsiElement container, PsiElement[] statements)
    throws IncorrectOperationException {
    PsiManager manager = PsiManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, true);
    if (statements.length == 0) {
      return null;
    }

    List<PsiClassType> exceptions = ExceptionUtil.getUnhandledExceptions(statements);
    if (exceptions.isEmpty()) {
      exceptions = ExceptionUtil.getThrownExceptions(statements);
      if (exceptions.isEmpty()) {
        exceptions = Collections.singletonList(factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_EXCEPTION, container.getResolveScope()));
      }
    }

    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append("try{\n}");
    for (PsiClassType exception : exceptions) {
      buffer.append("catch(Exception e){\n}");
    }
    if (myGenerateFinally) {
      buffer.append("finally{\n}");
    }
    String text = buffer.toString();
    PsiTryStatement tryStatement = (PsiTryStatement)factory.createStatementFromText(text, null);
    tryStatement = (PsiTryStatement)CodeStyleManager.getInstance(project).reformat(tryStatement);

    tryStatement = (PsiTryStatement)container.addAfter(tryStatement, statements[statements.length - 1]);

    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    SurroundWithUtil.indentCommentIfNecessary(tryBlock, statements);
    tryBlock.addRange(statements[0], statements[statements.length - 1]);

    PsiCatchSection[] catchSections = tryStatement.getCatchSections();

    for (int i = 0; i < exceptions.size(); i++) {
      PsiClassType exception = exceptions.get(i);
      String[] nameSuggestions = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, exception).names;
      String name = codeStyleManager.suggestUniqueVariableName(nameSuggestions[0], tryBlock, false);
      PsiCatchSection catchSection;
      try {
        catchSection = factory.createCatchSection(exception, name, null);
      }
      catch (IncorrectOperationException e) {
        Messages.showErrorDialog(
          project,
          CodeInsightLocalize.surroundWithTryCatchIncorrectTemplateMessage().get(),
          CodeInsightLocalize.surroundWithTryCatchIncorrectTemplateTitle().get()
        );
        return null;
      }
      catchSection = (PsiCatchSection)catchSections[i].replace(catchSection);
      codeStyleManager.shortenClassReferences(catchSection);
    }

    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    PsiCodeBlock firstCatch = tryStatement.getCatchBlocks()[0];
    return SurroundWithUtil.getRangeToSelect(firstCatch);
  }
}