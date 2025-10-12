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

/**
 * @author cdr
 */
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiKeyword;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

/**
 * changes 'class a extends b' to 'class a implements b' or vice versa
 */
public class ChangeExtendsToImplementsFix extends ExtendsListFix {
  private final LocalizeValue myName;

  public ChangeExtendsToImplementsFix(PsiClass aClass, PsiClassType classToExtendFrom) {
    super(aClass, classToExtendFrom, true);
    myName = JavaQuickFixLocalize.exchangeExtendsImplementsKeyword(aClass.isInterface() == myClassToExtendFrom.isInterface() ? PsiKeyword.IMPLEMENTS : PsiKeyword.EXTENDS, aClass.isInterface() == myClassToExtendFrom.isInterface() ? PsiKeyword.EXTENDS : PsiKeyword.IMPLEMENTS, myClassToExtendFrom.getName());
  }

  @Override
  @Nonnull
  public LocalizeValue getText() {
    return myName;
  }
}