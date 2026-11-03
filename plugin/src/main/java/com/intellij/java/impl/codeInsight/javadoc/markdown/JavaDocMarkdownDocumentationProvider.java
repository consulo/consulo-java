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
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.markdown.documentation.MarkdownDocumentationProvider;
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Markdown documentation comments of Java (JEP 467): the {@code ///} comments, rendered with {@link JavaDocMarkdownFlavourDescriptor}.
 */
@ExtensionImpl
public class JavaDocMarkdownDocumentationProvider implements MarkdownDocumentationProvider {
  public static final JavaDocMarkdownFlavourDescriptor FLAVOUR = new JavaDocMarkdownFlavourDescriptor();

  private static final List<String> LINE_PREFIXES = List.of("///");

  @Override
  public Class<? extends PsiElement> getCommentClass() {
    return PsiDocComment.class;
  }

  @Override
  public boolean isMarkdown(PsiElement comment) {
    return ((PsiDocComment)comment).isMarkdownComment();
  }

  @Override
  public List<String> getLinePrefixes() {
    return LINE_PREFIXES;
  }

  @Override
  public MarkdownFlavourDescriptor getFlavour() {
    return FLAVOUR;
  }

  @Override
  public Class<? extends PsiLanguageInjectionHost> getCodeFenceClass() {
    return PsiMarkdownCodeBlockImpl.class;
  }

  @Override
  public @Nullable String getCodeFenceInfo(PsiLanguageInjectionHost codeFence) {
    return ((PsiMarkdownCodeBlockImpl)codeFence).getCodeLanguageInfo();
  }
}
