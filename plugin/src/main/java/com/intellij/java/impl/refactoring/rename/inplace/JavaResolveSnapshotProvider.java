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
package com.intellij.java.impl.refactoring.rename.inplace;

import com.intellij.java.language.JavaLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.refactoring.ResolveSnapshotProvider;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

/**
 * User: Maxim.Mossienko
 * Date: 29.07.2009
 * Time: 14:07:20
 */
@ExtensionImpl
public class JavaResolveSnapshotProvider extends ResolveSnapshotProvider {
  @Override
  public ResolveSnapshot createSnapshot(PsiElement scope) {
    return new JavaResolveSnapshot(scope);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
