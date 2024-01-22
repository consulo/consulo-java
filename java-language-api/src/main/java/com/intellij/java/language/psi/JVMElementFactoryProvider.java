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
package com.intellij.java.language.psi;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPoint;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.language.Language;
import consulo.language.extension.LanguageExtension;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.Objects;

/**
 * @author Medvedev Max
 */
@ExtensionAPI(ComponentScope.PROJECT)
public interface JVMElementFactoryProvider extends LanguageExtension {
  ExtensionPointCacheKey<JVMElementFactoryProvider, Map<Language, JVMElementFactoryProvider>> CACHE_KEY = ExtensionPointCacheKey.groupBy("JVMElementFactoryProvider", JVMElementFactoryProvider::getLanguage);

  @Nonnull
  static JVMElementFactory forLanguageRequired(@Nonnull Project project, @jakarta.annotation.Nonnull Language language) {
    return Objects.requireNonNull(forLanguage(project, language), () -> "JVMElementFactoryProvider impl is not registered for language: " + language);
  }

  @Nullable
  static JVMElementFactory forLanguage(@Nonnull Project project, @jakarta.annotation.Nonnull Language language) {
    ExtensionPoint<JVMElementFactoryProvider> point = project.getExtensionPoint(JVMElementFactoryProvider.class);
    Map<Language, JVMElementFactoryProvider> map = point.getOrBuildCache(CACHE_KEY);
    JVMElementFactoryProvider provider = map.get(language);
    if (provider == null) {
      return null;
    }
    return provider.getFactory(project);
  }

  @jakarta.annotation.Nonnull
  JVMElementFactory getFactory(Project project);
}
