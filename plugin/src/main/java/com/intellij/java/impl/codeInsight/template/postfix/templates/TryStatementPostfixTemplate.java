/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import consulo.language.editor.postfixTemplate.PostfixTemplate;
import consulo.language.editor.postfixTemplate.PostfixTemplatesUtils;
import com.intellij.java.impl.codeInsight.generation.surroundWith.JavaWithTryCatchSurrounder;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.PsiTryStatement;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;

import jakarta.annotation.Nonnull;

public class TryStatementPostfixTemplate extends PostfixTemplate {

  protected TryStatementPostfixTemplate() {
    super("try", "try { exp } catch(Ex e) { e.printStackTrace(); }");
  }

  @Override
  public boolean isApplicable(@Nonnull PsiElement context, @Nonnull Document copyDocument, int newOffset) {
    return null != PsiTreeUtil.getNonStrictParentOfType(context, PsiStatement.class);
  }

  @Override
  public void expand(@Nonnull PsiElement context, @Nonnull Editor editor) {
    PsiStatement statement = PsiTreeUtil.getNonStrictParentOfType(context, PsiStatement.class);
    assert statement != null;

    PsiFile file = statement.getContainingFile();
    Project project = context.getProject();

    JavaWithTryCatchSurrounder surrounder = new JavaWithTryCatchSurrounder();
    TextRange range = surrounder.surroundElements(project, editor, new PsiElement[]{statement});

    if (range == null) {
      PostfixTemplatesUtils.showErrorHint(project, editor);
      return;
    }

    PsiElement element = file.findElementAt(range.getStartOffset());
    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
    assert tryStatement != null;
    PsiCodeBlock block = tryStatement.getTryBlock();
    assert block != null;
    PsiStatement statementInTry = ArrayUtil.getFirstElement(block.getStatements());
    if (null != statementInTry) {
      editor.getCaretModel().moveToOffset(statementInTry.getTextRange().getEndOffset());
    }
  }
}
