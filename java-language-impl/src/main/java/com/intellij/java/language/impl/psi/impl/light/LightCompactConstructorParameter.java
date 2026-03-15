// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.psi.PsiRecordComponent;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

import java.util.Objects;

public class LightCompactConstructorParameter extends LightParameter implements LightRecordMember {
  private final PsiRecordComponent myRecordComponent;

  public LightCompactConstructorParameter(String name,
                                          PsiType type,
                                          PsiElement declarationScope,
                                          PsiRecordComponent component) {
    super(name, type, declarationScope);
    myRecordComponent = component;
    setModifierList(new LightRecordComponentModifierList(this, myManager, myRecordComponent));
  }

  @Override
  public PsiElement getContext() {
    return getDeclarationScope();
  }

  @Override
  public PsiFile getContainingFile() {
    return getDeclarationScope().getContainingFile();
  }

  @Override
  public PsiRecordComponent getRecordComponent() {
    return myRecordComponent;
  }

  @Override
  public int getTextOffset() {
    return myRecordComponent.getTextOffset();
  }

  @Override
  public PsiElement getNavigationElement() {
    return myRecordComponent.getNavigationElement();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof LightCompactConstructorParameter &&
      myRecordComponent.equals(((LightCompactConstructorParameter)o).myRecordComponent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myRecordComponent);
  }
}
