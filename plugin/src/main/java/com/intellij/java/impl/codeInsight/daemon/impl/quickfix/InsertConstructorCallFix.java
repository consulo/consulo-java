/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.java.analysis.impl.psi.util.PsiMatchers;
import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiMatcherImpl;
import consulo.project.Project;

import javax.annotation.Nonnull;

public class InsertConstructorCallFix implements SyntheticIntentionAction, HighPriorityAction {
  protected final PsiMethod myConstructor;
  private final String myCall;

  public InsertConstructorCallFix(@Nonnull PsiMethod constructor, String call) {
    myConstructor = constructor;
    myCall = call;
  }

  @Override
  @Nonnull
  public String getText() {
    return JavaQuickFixBundle.message("insert.super.constructor.call.text", myCall);
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myConstructor.isValid() && myConstructor.getBody() != null && myConstructor.getBody().getLBrace() != null && myConstructor.getManager().isInProject(myConstructor);
  }

  @Nonnull
  @Override
  public PsiElement getElementToMakeWritable(@Nonnull PsiFile file) {
    return myConstructor;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) {
    PsiStatement superCall = JavaPsiFacade.getInstance(myConstructor.getProject()).getElementFactory().createStatementFromText(myCall, null);

    PsiCodeBlock body = myConstructor.getBody();
    PsiJavaToken lBrace = body.getLBrace();
    body.addAfter(superCall, lBrace);
    lBrace = (PsiJavaToken) new PsiMatcherImpl(body).firstChild(PsiMatchers.hasClass(PsiExpressionStatement.class)).firstChild(PsiMatchers.hasClass(PsiMethodCallExpression.class)).firstChild
        (PsiMatchers.hasClass(PsiExpressionList.class)).firstChild(PsiMatchers.hasClass(PsiJavaToken.class)).dot(PsiMatchers.hasText("(")).getElement();
    editor.getCaretModel().moveToOffset(lBrace.getTextOffset() + 1);
    LanguageUndoUtil.markPsiFileForUndo(file);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

}
