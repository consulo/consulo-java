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
package com.intellij.java.impl.spi;

import com.intellij.java.impl.codeInsight.navigation.BaseJavaGotoSuperHandler;
import com.intellij.java.impl.spi.psi.SPIClassProviderReferenceElement;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.spi.SPILanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.util.PsiTreeUtil;

import javax.annotation.Nonnull;

/**
 * User: anna
 */
@ExtensionImpl
public class SPIGotoSuperHandler extends BaseJavaGotoSuperHandler {
  @Override
  protected PsiNameIdentifierOwner getElement(PsiFile file, int offset) {
    final SPIClassProviderReferenceElement
      providerElement = PsiTreeUtil.getParentOfType(file.findElementAt(offset), SPIClassProviderReferenceElement.class);
    if (providerElement != null) {
      return (PsiClass)providerElement.resolve();
    }

    return null;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return SPILanguage.INSTANCE;
  }
}
