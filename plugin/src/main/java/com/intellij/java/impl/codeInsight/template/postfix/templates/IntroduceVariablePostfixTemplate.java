// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import com.intellij.java.impl.refactoring.introduceVariable.JavaIntroduceVariableHandlerBase;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiExpression;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.codeEditor.PersistentEditorSettings;
import consulo.document.Document;
import consulo.language.editor.refactoring.RefactoringSupportProvider;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateWithExpressionSelector;
import consulo.language.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import static com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

// todo: support for int[].var (parses as .class access!)
public class IntroduceVariablePostfixTemplate extends PostfixTemplateWithExpressionSelector implements DumbAware {
  public IntroduceVariablePostfixTemplate() {
    super("var", "T name = expr", selectorAllExpressionsWithCurrentOffset(IS_NON_VOID));
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    // for advanced stuff use ((PsiJavaCodeReferenceElement)expression).advancedResolve(true).getElement();
    JavaIntroduceVariableHandlerBase handler =
      (JavaIntroduceVariableHandlerBase)RefactoringSupportProvider.forLanguage(JavaLanguage.INSTANCE)
                                                                           .getIntroduceVariableHandler();
    assert handler != null;
    handler.invoke(expression.getProject(), editor, (PsiExpression)expression);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context,
                              @NotNull Document copyDocument, int newOffset) {
    // Non-inplace mode would require a modal dialog, which is not allowed under postfix templates
    return PersistentEditorSettings.getInstance().isVariableInplaceRenameEnabled() &&
      super.isApplicable(context, copyDocument, newOffset);
  }

  @Override
  protected void prepareAndExpandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    //no write action
    expandForChooseExpression(expression, editor);
  }
}