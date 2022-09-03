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
package com.intellij.java.language.psi;

import consulo.language.Language;
import consulo.project.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Medvedev Max
 */
@Deprecated
public class JVMElementFactories {
  @Nullable
  public static JVMElementFactory getFactory(@Nonnull Language language, @Nonnull Project project) {
    return JVMElementFactoryProvider.forLanguage(project, language);
  }

  @Nonnull
  public static JVMElementFactory requireFactory(@Nonnull Language language, @Nonnull Project project) {
    return JVMElementFactoryProvider.forLanguageRequired(project, language);
  }
}
