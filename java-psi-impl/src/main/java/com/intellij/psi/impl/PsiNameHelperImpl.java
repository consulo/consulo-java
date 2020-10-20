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
package com.intellij.psi.impl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.intellij.lang.java.lexer.JavaLexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiNameHelper;

@Singleton
public class PsiNameHelperImpl extends PsiNameHelper {

  @Override
  public boolean isIdentifier(@Nullable String text) {
    return isIdentifier(text, getLanguageLevel());
  }

  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.HIGHEST;
  }

  @Override
  public boolean isIdentifier(@Nullable String text, @Nonnull LanguageLevel languageLevel) {
    return text != null && StringUtil.isJavaIdentifier(text) && !JavaLexer.isKeyword(text, languageLevel);
  }

  @Override
  public boolean isKeyword(@javax.annotation.Nullable String text) {
    return text != null && JavaLexer.isKeyword(text, getLanguageLevel());
  }

  @Override
  public boolean isQualifiedName(@javax.annotation.Nullable String text) {
    if (text == null) return false;
    int index = 0;
    while (true) {
      int index1 = text.indexOf('.', index);
      if (index1 < 0) index1 = text.length();
      if (!isIdentifier(text.substring(index, index1))) return false;
      if (index1 == text.length()) return true;
      index = index1 + 1;
    }
  }

  public static PsiNameHelper getInstance() {
    return new PsiNameHelperImpl() {
      @Override
      protected LanguageLevel getLanguageLevel() {
        return LanguageLevel.HIGHEST;
      }
    };
  }

  @Inject
  private PsiNameHelperImpl() {
  }
}
