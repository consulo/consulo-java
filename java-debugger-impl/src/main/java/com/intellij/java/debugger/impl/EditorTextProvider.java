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
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.extension.ByLanguageValue;
import consulo.language.extension.LanguageExtension;
import consulo.language.extension.LanguageOneToOne;
import consulo.language.psi.PsiElement;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Provides text in the editor for Evaluate expression action
 * @author Maxim.Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface EditorTextProvider extends LanguageExtension {
  ExtensionPointCacheKey<EditorTextProvider, ByLanguageValue<EditorTextProvider>> KEY = ExtensionPointCacheKey.create("EditorTextProvider", LanguageOneToOne.build());

  @Nullable
  static EditorTextProvider forLanguage(@Nonnull Language language) {
    return Application.get().getExtensionPoint(EditorTextProvider.class).getOrBuildCache(KEY).get(language);
  }

  @Nullable
  TextWithImports getEditorText(PsiElement elementAtCaret);

  @Nullable
  Pair<PsiElement, TextRange> findExpression(PsiElement elementAtCaret, boolean allowMethodCalls);
}
