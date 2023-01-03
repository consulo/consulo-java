/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiUsesStatementStub;
import com.intellij.java.language.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PsiUsesStatementImpl extends JavaStubPsiElement<PsiUsesStatementStub> implements PsiUsesStatement {
  public PsiUsesStatementImpl(@Nonnull PsiUsesStatementStub stub) {
    super(stub, JavaStubElementTypes.USES_STATEMENT);
  }

  public PsiUsesStatementImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public PsiJavaCodeReferenceElement getClassReference() {
    return PsiTreeUtil.getChildOfType(this, PsiJavaCodeReferenceElement.class);
  }

  @Nullable
  @Override
  public PsiClassType getClassType() {
    PsiUsesStatementStub stub = getStub();
    PsiJavaCodeReferenceElement ref =
        stub != null ? JavaPsiFacade.getElementFactory(getProject()).createReferenceFromText(stub.getClassName(), this) : getClassReference();
    return ref != null ? new PsiClassReferenceType(ref, null, PsiAnnotation.EMPTY_ARRAY) : null;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitUsesStatement(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiUsesStatement";
  }
}