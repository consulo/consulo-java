// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiRecordHeaderStub;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiRecordComponent;
import com.intellij.java.language.psi.PsiRecordHeader;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElementVisitor;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class ClsRecordHeaderImpl extends ClsRepositoryPsiElement<PsiRecordHeaderStub> implements PsiRecordHeader {
  public ClsRecordHeaderImpl(@Nonnull PsiRecordHeaderStub stub) {
    super(stub);
  }

  @Override
  @Nonnull
  public PsiRecordComponent[] getRecordComponents() {
    return getStub().getChildrenByType(JavaStubElementTypes.RECORD_COMPONENT, PsiRecordComponent.ARRAY_FACTORY);
  }

  @Override
  @Nullable
  public PsiClass getContainingClass() {
    return (PsiClass) getParent();
  }

  @Override
  public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
    buffer.append('(');
    PsiRecordComponent[] parameters = getRecordComponents();
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) {
        buffer.append(", ");
      }
      appendText(parameters[i], indentLevel, buffer);
    }
    buffer.append(')');
  }

  @Override
  public String getText() {
    StringBuilder buffer = new StringBuilder();
    appendMirrorText(0, buffer);
    return buffer.toString();
  }

  @Override
  public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);
    setMirrors(getRecordComponents(), SourceTreeToPsiMap.<PsiRecordHeader>treeToPsiNotNull(element).getRecordComponents());
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitRecordHeader(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiRecordHeader";
  }
}
