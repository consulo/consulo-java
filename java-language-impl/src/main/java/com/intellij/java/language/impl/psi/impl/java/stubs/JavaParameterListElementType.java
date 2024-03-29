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

import consulo.language.ast.ASTNode;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import com.intellij.java.language.psi.PsiParameterList;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiParameterListStubImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiParameterListImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.ParameterListElement;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import jakarta.annotation.Nonnull;

import java.io.IOException;

/**
 * @author max
 */
public class JavaParameterListElementType extends JavaStubElementType<PsiParameterListStub, PsiParameterList> {
  public JavaParameterListElementType() {
    super("PARAMETER_LIST");
  }

  @Nonnull
  @Override
  public ASTNode createCompositeNode() {
    return new ParameterListElement();
  }

  @Override
  public PsiParameterList createPsi(@Nonnull final PsiParameterListStub stub) {
    return getPsiFactory(stub).createParameterList(stub);
  }

  @Override
  public PsiParameterList createPsi(@Nonnull final ASTNode node) {
    return new PsiParameterListImpl(node);
  }

  @Override
  public PsiParameterListStub createStub(final LighterAST tree, final LighterASTNode node, final StubElement parentStub) {
    return new PsiParameterListStubImpl(parentStub);
  }

  @Override
  public void serialize(@Nonnull final PsiParameterListStub stub, @Nonnull final StubOutputStream dataStream) throws IOException {
  }

  @Nonnull
  @Override
  public PsiParameterListStub deserialize(@Nonnull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PsiParameterListStubImpl(parentStub);
  }

  @Override
  public void indexStub(@Nonnull final PsiParameterListStub stub, @Nonnull final IndexSink sink) {
  }
}
