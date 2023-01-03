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
package com.intellij.java.language.impl.psi.impl.java.stubs;

import java.io.IOException;

import javax.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.java.language.impl.psi.impl.cache.RecordUtil;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiTypeParameterStubImpl;
import consulo.language.ast.LightTreeUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiTypeParameterImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.TypeParameterElement;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.index.io.StringRef;

/**
 * @author max
 */
public class JavaTypeParameterElementType extends JavaStubElementType<PsiTypeParameterStub, PsiTypeParameter> {
  public JavaTypeParameterElementType() {
    super("TYPE_PARAMETER");
  }

  @Nonnull
  @Override
  public ASTNode createCompositeNode() {
    return new TypeParameterElement();
  }

  @Override
  public PsiTypeParameter createPsi(@Nonnull final PsiTypeParameterStub stub) {
    return getPsiFactory(stub).createTypeParameter(stub);
  }

  @Override
  public PsiTypeParameter createPsi(@Nonnull final ASTNode node) {
    return new PsiTypeParameterImpl(node);
  }

  @Override
  public PsiTypeParameterStub createStub(final LighterAST tree, final LighterASTNode node, final StubElement parentStub) {
    final LighterASTNode id = LightTreeUtil.requiredChildOfType(tree, node, JavaTokenType.IDENTIFIER);
    final String name = RecordUtil.intern(tree.getCharTable(), id);
    return new PsiTypeParameterStubImpl(parentStub, name);
  }

  @Override
  public void serialize(@Nonnull final PsiTypeParameterStub stub, @Nonnull final StubOutputStream dataStream) throws IOException {
    String name = stub.getName();
    dataStream.writeName(name);
  }

  @Nonnull
  @Override
  public PsiTypeParameterStub deserialize(@Nonnull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    return new PsiTypeParameterStubImpl(parentStub, name);
  }

  @Override
  public void indexStub(@Nonnull final PsiTypeParameterStub stub, @Nonnull final IndexSink sink) {
  }
}
