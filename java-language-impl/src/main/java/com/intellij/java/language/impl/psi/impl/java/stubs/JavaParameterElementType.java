// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.java.language.impl.psi.impl.cache.RecordUtil;
import com.intellij.java.language.impl.psi.impl.cache.TypeInfo;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiParameterStubImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiParameterImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.java.ParameterElement;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiParameter;
import consulo.language.ast.LightTreeUtil;
import consulo.language.ast.ASTNode;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;

import java.io.IOException;

/**
 * @author max
 */
public class JavaParameterElementType extends JavaStubElementType<PsiParameterStub, PsiParameter> {
  public JavaParameterElementType() {
    super("PARAMETER");
  }

  @Override
  public ASTNode createCompositeNode() {
    return new ParameterElement(JavaElementType.PARAMETER);
  }

  @Override
  public PsiParameter createPsi(PsiParameterStub stub) {
    return getPsiFactory(stub).createParameter(stub);
  }

  @Override
  public PsiParameter createPsi(ASTNode node) {
    return new PsiParameterImpl(node);
  }

  @Override
  public PsiParameterStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    TypeInfo typeInfo = TypeInfo.create(tree, node, parentStub);
    LighterASTNode id = LightTreeUtil.requiredChildOfType(tree, node, JavaTokenType.IDENTIFIER);
    String name = RecordUtil.intern(tree.getCharTable(), id);
    return new PsiParameterStubImpl(parentStub, name, typeInfo, typeInfo.isEllipsis(), false);
  }

  @Override
  public void serialize(PsiParameterStub stub, StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    TypeInfo.writeTYPE(dataStream, stub.getType());
    dataStream.writeByte(((PsiParameterStubImpl)stub).getFlags());
  }

  @Override
  public PsiParameterStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = dataStream.readNameString();
    if (name == null)
      throw new IOException("corrupted indices");
    TypeInfo type = TypeInfo.readTYPE(dataStream);
    byte flags = dataStream.readByte();
    return new PsiParameterStubImpl(parentStub, name, type, flags);
  }

  @Override
  public void indexStub(PsiParameterStub stub, IndexSink sink) {
  }
}