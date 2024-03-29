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

import com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;

import static com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NOT_PRIMITIVE;
import static com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.JAVA_PSI_INFO;

public class SynchronizedStatementPostfixTemplate extends JavaStatementWrapPostfixTemplate {
  public SynchronizedStatementPostfixTemplate() {
    super("synchronized", "synchronized (expr)", JAVA_PSI_INFO, IS_NOT_PRIMITIVE);
  }

  @Override
  protected void afterExpand(@Nonnull PsiElement newStatement, @Nonnull Editor editor) {
    JavaPostfixTemplatesUtils.formatPsiCodeBlock(newStatement, editor);
  }

  @Nonnull
  @Override
  protected String getHead() {
    return "synchronized (";
  }

  @Nonnull
  @Override
  protected String getTail() {
    return ") {\nst;\n}";
  }

}