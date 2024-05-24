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

import com.intellij.java.impl.codeInsight.generation.surroundWith.JavaWithCastSurrounder;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.language.editor.postfixTemplate.PostfixTemplatesUtils;
import consulo.language.editor.refactoring.postfixTemplate.PostfixTemplateWithExpressionSelector;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

import static com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class CastExpressionPostfixTemplate extends PostfixTemplateWithExpressionSelector implements DumbAware {
  public CastExpressionPostfixTemplate() {
    super("cast", "((SomeType) expr)", selectorAllExpressionsWithCurrentOffset(IS_NON_VOID));
  }

  @Override
  protected void expandForChooseExpression(@Nonnull PsiElement expression, @Nonnull Editor editor) {
    PostfixTemplatesUtils.surround(new JavaWithCastSurrounder(), editor, expression);
  }
}