// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import consulo.annotation.DeprecationInfo;
import consulo.codeEditor.Editor;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.editor.postfixTemplate.PostfixTemplatePsiInfo;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateExpressionSelector;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateWithExpressionSelector;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@Deprecated
@DeprecationInfo("Use from platform")
public class ParenthesizedPostfixTemplate extends PostfixTemplateWithExpressionSelector {

  private final PostfixTemplatePsiInfo myPsiInfo;

  /**
   * @deprecated use {@link #ParenthesizedPostfixTemplate(PostfixTemplatePsiInfo, PostfixTemplateExpressionSelector, PostfixTemplateProvider)}
   */
  @Deprecated(forRemoval = true)
  public ParenthesizedPostfixTemplate(PostfixTemplatePsiInfo psiInfo,
                                      @Nonnull PostfixTemplateExpressionSelector selector) {
    this(psiInfo, selector, null);
  }

  public ParenthesizedPostfixTemplate(PostfixTemplatePsiInfo psiInfo,
                                      @Nonnull PostfixTemplateExpressionSelector selector,
                                      @Nullable PostfixTemplateProvider provider) {
    super(null, "par", "(expr)", selector, provider);
    myPsiInfo = psiInfo;
  }

  @Override
  protected void expandForChooseExpression(@Nonnull PsiElement expression, @Nonnull Editor editor) {
    expression.replace(myPsiInfo.createExpression(expression, "(", ")"));
  }
}