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
package com.intellij.java.language.impl.psi.presentation.java;

import consulo.annotation.component.ExtensionImpl;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import com.intellij.java.language.psi.PsiField;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class FieldPresentationProvider implements ItemPresentationProvider<PsiField> {
  @jakarta.annotation.Nonnull
  @Override
  public Class<PsiField> getItemClass() {
    return PsiField.class;
  }

  @Nonnull
  @Override
  public ItemPresentation getPresentation(PsiField item) {
    return JavaPresentationUtil.getFieldPresentation(item);
  }
}
