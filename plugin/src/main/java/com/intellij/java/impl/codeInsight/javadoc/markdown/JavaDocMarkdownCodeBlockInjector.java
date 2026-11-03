/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.impl.codeInsight.javadoc.markdown;

import com.intellij.java.language.impl.psi.impl.source.javadoc.PsiMarkdownCodeBlockImpl;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.markdown.documentation.MarkdownDocumentationCodeFenceInjector;

/**
 * Injects the language of the fenced code blocks of Markdown documentation comments ({@code ///}), see
 * {@link JavaDocMarkdownDocumentationProvider}.
 */
@ExtensionImpl
public class JavaDocMarkdownCodeBlockInjector extends MarkdownDocumentationCodeFenceInjector {
  @Override
  public Class<? extends PsiLanguageInjectionHost> getElementClass() {
    return PsiMarkdownCodeBlockImpl.class;
  }
}
