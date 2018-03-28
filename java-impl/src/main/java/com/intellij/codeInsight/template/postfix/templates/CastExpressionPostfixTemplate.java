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
package com.intellij.codeInsight.template.postfix.templates;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.generation.surroundWith.JavaWithCastSurrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiExpression;

public class CastExpressionPostfixTemplate extends ExpressionPostfixTemplateWithChooser {
  public CastExpressionPostfixTemplate() {
    super("cast", "((SomeType) expr)");
  }

  @Override
  protected void doIt(@Nonnull final Editor editor, @Nonnull final PsiExpression expression) {
    PostfixTemplatesUtils.surround(new JavaWithCastSurrounder(), editor, expression);
  }
}