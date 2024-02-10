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
package com.intellij.java.impl.testIntegration.createTest;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.language.Language;
import consulo.language.extension.ByLanguageValue;
import consulo.language.extension.LanguageExtension;
import consulo.language.extension.LanguageOneToOne;
import consulo.language.psi.PsiElement;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Maxim.Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface TestGenerator extends LanguageExtension {
  ExtensionPointCacheKey<TestGenerator, ByLanguageValue<TestGenerator>> KEY = ExtensionPointCacheKey.create("TestGenerator", LanguageOneToOne.build());

  @Nullable
  static TestGenerator forLanguage(@Nonnull Language language) {
    return Application.get().getExtensionPoint(TestGenerator.class).getOrBuildCache(KEY).get(language);
  }

  /**
   *
   * @return generated test (i.e. PsiClass)
   */
  @Nullable
  PsiElement generateTest(final Project project, final CreateTestDialog d);

  /**
   * should return text to show in dialog
   */
  String toString();
}
