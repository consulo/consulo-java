// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiRequiresStatementStub;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.PsiJavaModuleReference;
import com.intellij.java.language.psi.PsiJavaModuleReferenceElement;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.PsiRequiresStatement;
import consulo.application.util.AtomicNotNullLazyValue;
import consulo.application.util.NotNullLazyValue;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.stub.StubElement;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;

public class ClsRequiresStatementImpl extends ClsRepositoryPsiElement<PsiRequiresStatementStub> implements PsiRequiresStatement {
  private final NotNullLazyValue<PsiJavaModuleReferenceElement> myModuleReference;

  public ClsRequiresStatementImpl(PsiRequiresStatementStub stub) {
    super(stub);
    myModuleReference = new AtomicNotNullLazyValue<PsiJavaModuleReferenceElement>() {
      @Nonnull
      @Override
      protected PsiJavaModuleReferenceElement compute() {
        return new ClsJavaModuleReferenceElementImpl(ClsRequiresStatementImpl.this, getStub().getModuleName());
      }
    };
  }

  @Override
  public PsiJavaModuleReferenceElement getReferenceElement() {
    return myModuleReference.getValue();
  }

  @Override
  public String getModuleName() {
    return getStub().getModuleName();
  }

  @Override
  public PsiJavaModuleReference getModuleReference() {
    return myModuleReference.getValue().getReference();
  }

  @Override
  public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
    StringUtil.repeatSymbol(buffer, ' ', indentLevel);
    buffer.append("requires ");
    appendText(getModifierList(), indentLevel, buffer);
    buffer.append(getModuleName()).append(";\n");
  }

  @Override
  public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.REQUIRES_STATEMENT);
    setMirror(getModifierList(), SourceTreeToPsiMap.<PsiRequiresStatement>treeToPsiNotNull(element).getModifierList());
  }

  @Override
  public PsiModifierList getModifierList() {
    StubElement<PsiModifierList> childStub = getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST);
    return childStub != null ? childStub.getPsi() : null;
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Override
  public String toString() {
    return "PsiRequiresStatement";
  }
}