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
package com.intellij.java.language.impl.psi.impl.light;

import jakarta.annotation.Nonnull;

import consulo.language.Language;
import com.intellij.java.language.psi.JavaElementVisitor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiEllipsisType;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;

/**
 * @author peter
 */
public class LightParameter extends LightVariableBuilder<LightVariableBuilder> implements PsiParameter {
  public static final LightParameter[] EMPTY_ARRAY = new LightParameter[0];

  private final String myName;
  private final PsiElement myDeclarationScope;
  private final boolean myVarArgs;

  public LightParameter(@Nonnull String name, @Nonnull PsiType type, PsiElement declarationScope, Language language) {
    this(name, type, declarationScope, language, type instanceof PsiEllipsisType);
  }

  public LightParameter(@Nonnull String name, @jakarta.annotation.Nonnull PsiType type, PsiElement declarationScope, Language language, boolean isVarArgs) {
    super(declarationScope.getManager(), name, type, language);
    myName = name;
    myDeclarationScope = declarationScope;
    myVarArgs = isVarArgs;
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiElement getDeclarationScope() {
    return myDeclarationScope;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameter(this);
    }
  }

  public String toString() {
    return "Light Parameter";
  }

  @Override
  public boolean isVarArgs() {
    return myVarArgs;
  }

  @Override
  @jakarta.annotation.Nonnull
  public String getName() {
    return myName;
  }
}
