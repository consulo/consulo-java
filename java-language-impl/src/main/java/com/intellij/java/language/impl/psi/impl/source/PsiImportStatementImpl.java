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
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiImportStatement;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElementVisitor;
import consulo.util.collection.ArrayFactory;
import jakarta.annotation.Nonnull;

public class PsiImportStatementImpl extends PsiImportStatementBaseImpl implements PsiImportStatement {
  public static final PsiImportStatementImpl[] EMPTY_ARRAY = new PsiImportStatementImpl[0];
  public static final ArrayFactory<PsiImportStatementImpl> ARRAY_FACTORY = new ArrayFactory<PsiImportStatementImpl>() {
    @Nonnull
    @Override
    public PsiImportStatementImpl[] create(final int count) {
      return count == 0 ? EMPTY_ARRAY : new PsiImportStatementImpl[count];
    }
  };

  public PsiImportStatementImpl(final PsiImportStatementStub stub) {
    super(stub, JavaStubElementTypes.IMPORT_STATEMENT);
  }

  public PsiImportStatementImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public String getQualifiedName() {
    final PsiJavaCodeReferenceElement reference = getImportReference();
    return reference == null ? null : reference.getCanonicalText();
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitImportStatement(this);
    } else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiImportStatement";
  }
}