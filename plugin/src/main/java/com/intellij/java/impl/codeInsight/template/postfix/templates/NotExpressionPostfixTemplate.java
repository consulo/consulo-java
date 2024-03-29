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

import com.intellij.java.impl.codeInsight.CodeInsightServicesUtil;
import com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import consulo.codeEditor.Editor;
import consulo.util.lang.function.Condition;
import com.intellij.java.language.psi.PsiExpression;
import jakarta.annotation.Nonnull;

public class NotExpressionPostfixTemplate extends ExpressionPostfixTemplateWithChooser {
  private static final Condition<PsiExpression> BOOLEAN_TYPE_CONDITION = new Condition<PsiExpression>() {
    @Override
    public boolean value(PsiExpression expression) {
      return JavaPostfixTemplatesUtils.isBoolean(expression.getType());
    }
  };

  public NotExpressionPostfixTemplate() {
    super("not",  "!expr");
  }

  public NotExpressionPostfixTemplate(String alias) {
    super(alias, alias, "!expr");
  }

  @Override
  protected void doIt(@Nonnull Editor editor, @Nonnull PsiExpression expression) {
    expression.replace(CodeInsightServicesUtil.invertCondition(expression));
  }

  @Nonnull
  @Override
  protected Condition<PsiExpression> getTypeCondition() {
    return BOOLEAN_TYPE_CONDITION;
  }
}