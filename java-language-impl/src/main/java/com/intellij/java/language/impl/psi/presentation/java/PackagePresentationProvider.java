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
import consulo.application.AllIcons;
import consulo.ui.ex.ColoredItemPresentation;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.colorScheme.TextAttributesKey;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class PackagePresentationProvider implements ItemPresentationProvider<PsiJavaPackage> {
  @Nonnull
  @Override
  public Class<PsiJavaPackage> getItemClass() {
    return PsiJavaPackage.class;
  }

  @Override
  public ItemPresentation getPresentation(final PsiJavaPackage aPackage) {
    return new ColoredItemPresentation() {
      @Override
      public TextAttributesKey getTextAttributesKey() {
        return null;
      }

      @Override
      public String getPresentableText() {
        return aPackage.getName();
      }

      @Override
      public String getLocationString() {
        return aPackage.getQualifiedName();
      }

      @Override
      public Image getIcon() {
        return AllIcons.Nodes.Package;
      }
    };
  }
}
