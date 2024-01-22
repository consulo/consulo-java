/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.generation;

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiParameter;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.language.Language;
import consulo.language.extension.ByLanguageValue;
import consulo.language.extension.LanguageExtension;
import consulo.language.extension.LanguageOneToOne;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Max Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface ConstructorBodyGenerator extends LanguageExtension {
  ExtensionPointCacheKey<ConstructorBodyGenerator, ByLanguageValue<ConstructorBodyGenerator>> KEY = ExtensionPointCacheKey.create("ConstructorBodyGenerator", LanguageOneToOne.build());

  @Nullable
  static ConstructorBodyGenerator forLanguage(@jakarta.annotation.Nonnull Language language) {
    return Application.get().getExtensionPoint(ConstructorBodyGenerator.class).getOrBuildCache(KEY).get(language);
  }

  void generateFieldInitialization(@jakarta.annotation.Nonnull StringBuilder buffer, @jakarta.annotation.Nonnull PsiField[] fields, @jakarta.annotation.Nonnull PsiParameter[] parameters);

  void generateSuperCallIfNeeded(@jakarta.annotation.Nonnull StringBuilder buffer, @jakarta.annotation.Nonnull PsiParameter[] parameters);

  StringBuilder start(StringBuilder buffer, @Nonnull String name, @jakarta.annotation.Nonnull PsiParameter[] parameters);

  void finish(StringBuilder builder);
}
