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

import javax.annotation.Nonnull;

import consulo.language.extension.LanguageExtension;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiParameter;

/**
* @author Max Medvedev
*/
public interface ConstructorBodyGenerator {
  LanguageExtension<ConstructorBodyGenerator> INSTANCE = new LanguageExtension<ConstructorBodyGenerator>("consulo.java.constructorBodyGenerator");

  void generateFieldInitialization(@Nonnull StringBuilder buffer, @Nonnull PsiField[] fields, @Nonnull PsiParameter[] parameters);

  void generateSuperCallIfNeeded(@Nonnull StringBuilder buffer, @Nonnull PsiParameter[] parameters);

  StringBuilder start(StringBuilder buffer, @Nonnull String name, @Nonnull PsiParameter[] parameters);

  void finish(StringBuilder builder);
}
