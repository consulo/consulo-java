// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import com.intellij.java.impl.refactoring.introduceField.JavaIntroduceFieldHandlerBase;
import com.intellij.java.language.JavaLanguage;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.RefactoringSupportProvider;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateWithExpressionSelector;
import consulo.language.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import static com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class IntroduceFieldPostfixTemplate extends PostfixTemplateWithExpressionSelector implements DumbAware {
  public IntroduceFieldPostfixTemplate() {
    super("field", "myField = expr", selectorAllExpressionsWithCurrentOffset(IS_NON_VOID));
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    RefactoringSupportProvider supportProvider = RefactoringSupportProvider.forLanguage(JavaLanguage.INSTANCE);
    JavaIntroduceFieldHandlerBase handler = (JavaIntroduceFieldHandlerBase)supportProvider.getIntroduceFieldHandler();
    assert handler != null;
    handler.invoke(expression.getProject(), expression, editor);
  }

  @Override
  protected void prepareAndExpandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    //no write action
    expandForChooseExpression(expression, editor);
  }
}