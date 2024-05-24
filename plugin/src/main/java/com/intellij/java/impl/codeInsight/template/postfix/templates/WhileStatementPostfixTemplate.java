// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import com.intellij.java.language.LanguageLevel;
import consulo.application.dumb.DumbAware;
import jakarta.annotation.Nonnull;

import java.util.Collections;

public class WhileStatementPostfixTemplate extends JavaEditablePostfixTemplate implements DumbAware {
  public WhileStatementPostfixTemplate(@Nonnull JavaPostfixTemplateProvider provider) {
    super("while", "while ($EXPR$) {\n$END$\n}", "while (expr) {}",
          Collections.singleton(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateBooleanExpressionCondition()),
          LanguageLevel.JDK_1_3, true, provider);
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }
}

