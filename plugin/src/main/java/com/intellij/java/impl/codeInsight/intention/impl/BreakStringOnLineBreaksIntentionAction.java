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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiJavaToken;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

/**
 * @author Danila Ponomarenko
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.BreakStringOnLineBreaksIntentionAction", categories = {"Java", "Refactorings"}, fileExtensions = "java")
public class BreakStringOnLineBreaksIntentionAction extends PsiElementBaseIntentionAction {
  @Override
  @RequiredReadAction
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }

    final PsiJavaToken token = (PsiJavaToken)element;

    if (token.getTokenType() != JavaTokenType.STRING_LITERAL) {
      return false;
    }

    final String text = token.getText();
    if (text == null) {
      return false;
    }

    final int indexOfSlashN = text.indexOf("\\n");
    if (indexOfSlashN == -1 || Comparing.equal(text.substring(indexOfSlashN, text.length()), "\\n\"")){
      return false;
    }

    final int indexOfSlashNSlashR = text.indexOf("\\n\\r");
    return indexOfSlashNSlashR == -1 || !Comparing.equal(text.substring(indexOfSlashNSlashR, text.length()), "\\n\\r\"");
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiJavaToken)) {
      return;
    }

    final PsiJavaToken token = (PsiJavaToken)element;

    if (token.getTokenType() != JavaTokenType.STRING_LITERAL) {
      return;
    }


    final String text = token.getText();
    if (text == null) {
      return;
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    token.replace(factory.createExpressionFromText(breakOnLineBreaks(text), element));
  }


  @Nonnull
  private static String breakOnLineBreaks(@Nonnull String string) {
    final String result = StringUtil.replace(
      string,
      new String[]{"\\n\\r", "\\n"},
      new String[]{"\\n\\r\" + \n\"", "\\n\" + \n\""}
    );

    final String redundantSuffix = " + \n\"\"";

    return result.endsWith(redundantSuffix) ? result.substring(0, result.length() - redundantSuffix.length()) : result;
  }

  @Nonnull
  @Override
  public String getText() {
    return CodeInsightLocalize.intentionBreakStringOnLineBreaksText().get();
  }
}
