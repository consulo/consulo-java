// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.java.stubs.PsiUsesStatementStub;
import com.intellij.java.language.impl.psi.impl.source.PsiClassReferenceType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiUsesStatement;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.util.lang.StringUtil;

import javax.annotation.Nonnull;

public class ClsUsesStatementImpl extends ClsRepositoryPsiElement<PsiUsesStatementStub> implements PsiUsesStatement {
  private final ClsJavaCodeReferenceElementImpl myClassReference;

  public ClsUsesStatementImpl(PsiUsesStatementStub stub) {
    super(stub);
    myClassReference = new ClsJavaCodeReferenceElementImpl(this, stub.getClassName());
  }

  @Override
  public PsiJavaCodeReferenceElement getClassReference() {
    return myClassReference;
  }

  @Override
  public PsiClassType getClassType() {
    return new PsiClassReferenceType(myClassReference, null, PsiAnnotation.EMPTY_ARRAY);
  }

  @Override
  public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
    StringUtil.repeatSymbol(buffer, ' ', indentLevel);
    buffer.append("uses ").append(myClassReference.getCanonicalText()).append(";\n");
  }

  @Override
  public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.USES_STATEMENT);
    setMirror(getClassReference(), SourceTreeToPsiMap.<PsiUsesStatement>treeToPsiNotNull(element).getClassReference());
  }

  @Override
  public String toString() {
    return "PsiUsesStatement";
  }
}