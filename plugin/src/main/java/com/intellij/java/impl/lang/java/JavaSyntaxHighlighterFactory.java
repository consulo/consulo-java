/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.lang.java;

import javax.annotation.Nonnull;
import com.intellij.java.impl.ide.highlighter.JavaFileHighlighter;
import com.intellij.java.language.JavaLanguage;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import consulo.fileTypes.LanguageVersionableSyntaxHighlighterFactory;
import consulo.lang.LanguageVersion;

public class JavaSyntaxHighlighterFactory extends LanguageVersionableSyntaxHighlighterFactory
{
  public JavaSyntaxHighlighterFactory() {
    super(JavaLanguage.INSTANCE);
  }

  @Nonnull
  @Override
  public SyntaxHighlighter getSyntaxHighlighter(@Nonnull LanguageVersion languageVersion) {
    return new JavaFileHighlighter(languageVersion);
  }
}
