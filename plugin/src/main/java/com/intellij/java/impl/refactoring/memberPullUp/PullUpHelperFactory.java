/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.memberPullUp;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.language.Language;
import consulo.language.extension.ByLanguageValue;
import consulo.language.extension.LanguageExtension;
import consulo.language.extension.LanguageOneToOne;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Created by Max Medvedev on 10/4/13
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PullUpHelperFactory extends LanguageExtension {
  ExtensionPointCacheKey<PullUpHelperFactory, ByLanguageValue<PullUpHelperFactory>> KEY = ExtensionPointCacheKey.create("PullUpHelperFactory", LanguageOneToOne.build());

  @Nullable
  static PullUpHelperFactory forLanguage(@Nonnull Language language) {
    return Application.get().getExtensionPoint(PullUpHelperFactory.class).getOrBuildCache(KEY).get(language);
  }

  PullUpHelper createPullUpHelper(PullUpData data);
}
