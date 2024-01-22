/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertToStringLiteralAction", categories = {"Java", "Strings"}, fileExtensions = "java")
public class ConvertToStringLiteralAction implements IntentionAction {
  @Nonnull
  @Override
  public String getText() {
    return JavaQuickFixBundle.message("convert.to.string.text");
  }

  @Override
  public boolean isAvailable(@jakarta.annotation.Nonnull final Project project, final Editor editor, final PsiFile file) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    return PsiUtil.isJavaToken(element, JavaTokenType.CHARACTER_LITERAL);
  }

  @Override
  public void invoke(@jakarta.annotation.Nonnull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element != null && PsiUtil.isJavaToken(element, JavaTokenType.CHARACTER_LITERAL)) {
      final String text = StringUtil.unescapeStringCharacters(element.getText());
      final int length = text.length();
      if (length > 1 && text.charAt(0) == '\'' && text.charAt(length - 1) == '\'') {
        final String value = StringUtil.escapeStringCharacters(text.substring(1, length - 1));
        final PsiExpression expression = JavaPsiFacade.getElementFactory(project).createExpressionFromText('"' + value + '"', null);
        final PsiElement literal = expression.getFirstChild();
        if (literal != null && PsiUtil.isJavaToken(literal, JavaTokenType.STRING_LITERAL)) {
          element.replace(literal);
        }
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
