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
package com.intellij.java.impl.codeInsight.template.impl;

import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.Result;
import consulo.language.editor.template.TextResult;

/**
 * @author Maxim
 */
public class TextExpression extends Expression {
  private final String myString;

  public TextExpression(String string) { myString = string; }

  @Override
  public Result calculateResult(ExpressionContext expressionContext) {
    return new TextResult(myString);
  }

  @Override
  public Result calculateQuickResult(ExpressionContext expressionContext) {
    return calculateResult(expressionContext);
  }

  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext expressionContext) {
    return LookupElement.EMPTY_ARRAY;
  }
}
