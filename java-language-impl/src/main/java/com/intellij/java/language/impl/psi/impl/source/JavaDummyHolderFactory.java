/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.JavaLanguage;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.impl.psi.DummyHolder;
import consulo.language.impl.psi.HolderFactory;
import consulo.language.impl.ast.TreeElement;
import consulo.language.util.CharTable;

import jakarta.annotation.Nonnull;

public class JavaDummyHolderFactory implements HolderFactory {
  @Override
  public DummyHolder createHolder(@Nonnull final PsiManager manager, final TreeElement contentElement, final PsiElement context) {
    return new JavaDummyHolder(manager, contentElement, context);
  }

  @Override
  public DummyHolder createHolder(@Nonnull final PsiManager manager,
                                  final TreeElement contentElement, final PsiElement context, final CharTable table) {
    return new JavaDummyHolder(manager, contentElement, context, table);
  }

  @Override
  public DummyHolder createHolder(@Nonnull final PsiManager manager, final PsiElement context) {
    return new JavaDummyHolder(manager, context);
  }

  @Override
  public DummyHolder createHolder(@Nonnull final PsiManager manager, final Language language, final PsiElement context) {
    return language == JavaLanguage.INSTANCE ? new JavaDummyHolder(manager, context) : new DummyHolder(manager, language, context);
  }

  @Override
  public DummyHolder createHolder(@Nonnull final PsiManager manager, final PsiElement context, final CharTable table) {
    return new JavaDummyHolder(manager, context, table);
  }

  @Override
  public DummyHolder createHolder(@Nonnull final PsiManager manager, final CharTable table, final Language language) {
    return new JavaDummyHolder(manager, table);
  }

  @Override
  public DummyHolder createHolder(@Nonnull final PsiManager manager, final CharTable table, final boolean validity) {
    return new JavaDummyHolder(manager, table, validity);
  }
}