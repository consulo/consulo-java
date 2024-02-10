/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.javadoc.JavadocTagInfo;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;

import jakarta.annotation.Nonnull;

/**
 * @author mike
 */
public class SimpleDocTagInfo implements JavadocTagInfo {
  private final String myName;
  private final Class[] myContexts;
  private final boolean myInline;
  private final LanguageLevel myLanguageLevel;

  public SimpleDocTagInfo(@Nonnull String name, @Nonnull LanguageLevel level, boolean isInline, @Nonnull Class... contexts) {
    myName = name;
    myContexts = contexts;
    myInline = isInline;
    myLanguageLevel = level;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isInline() {
    return myInline;
  }

  @Override
  public boolean isValidInContext(PsiElement element) {
    if (element != null && PsiUtil.getLanguageLevel(element).compareTo(myLanguageLevel) < 0) {
      return false;
    }
    for (Class context : myContexts) {
      if (context.isInstance(element)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String checkTagValue(PsiDocTagValue value) {
    return null;
  }

  @Override
  public PsiReference getReference(PsiDocTagValue value) {
    return null;
  }
}