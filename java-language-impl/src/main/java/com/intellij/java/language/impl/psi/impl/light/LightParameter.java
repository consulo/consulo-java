// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiEllipsisType;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

public class LightParameter extends LightVariableBuilder<LightVariableBuilder<?>> implements PsiParameter {
  private final PsiElement myDeclarationScope;
  private final boolean myVarArgs;

  public LightParameter(@NonNls @Nonnull String name, @Nonnull PsiType type, @Nonnull PsiElement declarationScope) {
    this(name, type, declarationScope, declarationScope.getLanguage());
  }

  public LightParameter(@NonNls @Nonnull String name,
                        @Nonnull PsiType type,
                        @Nonnull PsiElement declarationScope,
                        @Nonnull Language language) {
    this(name, type, declarationScope, language, type instanceof PsiEllipsisType);
  }

  public LightParameter(@NonNls @Nonnull String name,
                        @Nonnull PsiType type,
                        @Nonnull PsiElement declarationScope,
                        @Nonnull Language language,
                        boolean isVarArgs) {
    this(name, type, declarationScope, language, new LightModifierList(declarationScope.getManager()), isVarArgs);
  }

  public LightParameter(@NonNls @Nonnull String name,
                        @Nonnull PsiType type,
                        @Nonnull PsiElement declarationScope,
                        @Nonnull Language language,
                        @Nonnull LightModifierList modifierList,
                        boolean isVarArgs) {
    super(declarationScope.getManager(), name, type, language, modifierList);
    myDeclarationScope = declarationScope;
    myVarArgs = isVarArgs;
  }

  @Override
  public @Nonnull PsiElement getDeclarationScope() {
    return myDeclarationScope;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameter(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "Light Parameter";
  }

  @Override
  public boolean isVarArgs() {
    return myVarArgs;
  }
}
