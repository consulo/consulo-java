// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.light;

import consulo.language.impl.psi.LightElement;

import com.intellij.java.language.psi.JavaElementVisitor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiIdentifier;
import consulo.language.psi.PsiMirrorElement;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeElement;
import consulo.language.util.IncorrectOperationException;
import org.jspecify.annotations.Nullable;

public class LightParameterWrapper extends LightElement implements PsiParameter, PsiMirrorElement {

  private final PsiParameter myPrototype;
  private final PsiSubstitutor mySubstitutor;

  public LightParameterWrapper(PsiParameter prototype, PsiSubstitutor substitutor) {
    super(prototype.getManager(), prototype.getLanguage());
    myPrototype = prototype;
    mySubstitutor = substitutor;
  }

  @Override
  public void accept(PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameter(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
  @Override
  public PsiParameter getPrototype() {
    return myPrototype;
  }

  @Override
  public String getName() {
    return myPrototype.getName();
  }

  @Override
  public PsiType getType() {
    return mySubstitutor.substitute(myPrototype.getType());
  }

  @Override
  public @Nullable PsiModifierList getModifierList() {
    return myPrototype.getModifierList();
  }

  @Override
  public boolean hasModifierProperty(@PsiModifier.ModifierConstant String name) {
    return myPrototype.hasModifierProperty(name);
  }

  @Override
  public PsiElement getDeclarationScope() {
    return myPrototype.getDeclarationScope();
  }

  @Override
  public @Nullable PsiExpression getInitializer() {
    return myPrototype.getInitializer();
  }

  @Override
  public boolean isVarArgs() {
    return myPrototype.isVarArgs();
  }

  @Override
  public @Nullable PsiTypeElement getTypeElement() {
    return myPrototype.getTypeElement();
  }

  @Override
  public boolean hasInitializer() {
    return myPrototype.hasInitializer();
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public @Nullable Object computeConstantValue() {
    return myPrototype.computeConstantValue();
  }

  @Override
  public @Nullable PsiIdentifier getNameIdentifier() {
    return myPrototype.getNameIdentifier();
  }

  @Override
  public PsiElement setName(String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public String toString() {
    return "List PSI parameter wrapper: " + myPrototype;
  }
}
