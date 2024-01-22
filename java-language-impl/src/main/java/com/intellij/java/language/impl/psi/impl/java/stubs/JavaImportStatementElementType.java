/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiImportStatementStubImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiImportStatementImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiImportStaticStatementImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.java.ImportStaticStatementElement;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiImportStatementBase;
import consulo.index.io.StringRef;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.io.IOException;

/**
 * @author max
 */
public abstract class JavaImportStatementElementType extends JavaStubElementType<PsiImportStatementStub, PsiImportStatementBase> {
  public JavaImportStatementElementType(@NonNls @Nonnull final String id) {
    super(id);
  }

  @Override
  public PsiImportStatementBase createPsi(@Nonnull final PsiImportStatementStub stub) {
    return getPsiFactory(stub).createImportStatement(stub);
  }

  @Override
  public PsiImportStatementBase createPsi(@jakarta.annotation.Nonnull final ASTNode node) {
    if (node instanceof ImportStaticStatementElement) {
      return new PsiImportStaticStatementImpl(node);
    } else {
      return new PsiImportStatementImpl(node);
    }
  }

  @Override
  public PsiImportStatementStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    boolean isOnDemand = false;
    String refText = null;

    for (LighterASTNode child : tree.getChildren(node)) {
      IElementType type = child.getTokenType();
      if (type == JavaElementType.JAVA_CODE_REFERENCE || type == JavaElementType.IMPORT_STATIC_REFERENCE) {
        refText = JavaSourceUtil.getReferenceText(tree, child);
      } else if (type == JavaTokenType.ASTERISK) {
        isOnDemand = true;
      }
    }

    byte flags = PsiImportStatementStubImpl.packFlags(isOnDemand, node.getTokenType() == JavaElementType.IMPORT_STATIC_STATEMENT);
    return new PsiImportStatementStubImpl(parentStub, refText, flags);
  }

  @Override
  public void serialize(@jakarta.annotation.Nonnull final PsiImportStatementStub stub, @Nonnull final StubOutputStream dataStream) throws IOException {
    dataStream.writeByte(((PsiImportStatementStubImpl) stub).getFlags());
    dataStream.writeName(stub.getImportReferenceText());
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiImportStatementStub deserialize(@jakarta.annotation.Nonnull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    final byte flags = dataStream.readByte();
    final StringRef refText = dataStream.readName();
    return new PsiImportStatementStubImpl(parentStub, StringRef.toString(refText), flags);
  }

  @Override
  public void indexStub(@jakarta.annotation.Nonnull final PsiImportStatementStub stub, @Nonnull final IndexSink sink) {
  }
}
