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
package com.intellij.java.impl.ide.util;

import consulo.annotation.component.ExtensionImpl;
import consulo.ide.IdeBundle;
import consulo.language.editor.refactoring.util.DeleteTypeDescriptionLocation;
import com.intellij.java.language.psi.*;
import consulo.language.psi.ElementDescriptionLocation;
import consulo.language.psi.ElementDescriptionProvider;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaDeleteTypeDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(@Nonnull final PsiElement element, @Nonnull final ElementDescriptionLocation location) {
    if (location instanceof DeleteTypeDescriptionLocation && ((DeleteTypeDescriptionLocation) location).isPlural()) {
      if (element instanceof PsiMethod) {
        return IdeBundle.message("prompt.delete.method", 2);
      } else if (element instanceof PsiField) {
        return IdeBundle.message("prompt.delete.field", 2);
      } else if (element instanceof PsiClass) {
        if (((PsiClass) element).isInterface()) {
          return IdeBundle.message("prompt.delete.interface", 2);
        }
        return element instanceof PsiTypeParameter
            ? IdeBundle.message("prompt.delete.type.parameter", 2)
            : IdeBundle.message("prompt.delete.class", 2);
      } else if (element instanceof PsiJavaPackage) {
        return IdeBundle.message("prompt.delete.package", 2);
      }
    }
    return null;
  }
}
