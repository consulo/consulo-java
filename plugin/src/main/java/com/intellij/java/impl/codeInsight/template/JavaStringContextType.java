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
package com.intellij.java.impl.codeInsight.template;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiJavaToken;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.template.context.BaseTemplateContextType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaStringContextType extends BaseTemplateContextType {
  public JavaStringContextType() {
    super("JAVA_STRING", LocalizeValue.localizeTODO("String"), JavaGenericContextType.class);
  }

  @Override
  public boolean isInContext(@Nonnull final PsiFile file, final int offset) {
    if (PsiUtilCore.getLanguageAtOffset(file, offset).isKindOf(JavaLanguage.INSTANCE)) {
      return isStringLiteral(file.findElementAt(offset));
    }
    return false;
  }

  static boolean isStringLiteral(PsiElement element) {
    return element instanceof PsiJavaToken && ((PsiJavaToken) element).getTokenType() == JavaTokenType.STRING_LITERAL;
  }
}
