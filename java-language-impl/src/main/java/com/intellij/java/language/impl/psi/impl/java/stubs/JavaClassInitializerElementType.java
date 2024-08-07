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
import com.intellij.java.language.psi.PsiClassInitializer;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiClassInitializerStubImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiClassInitializerImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.ClassInitializerElement;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import jakarta.annotation.Nonnull;

import java.io.IOException;

/**
 * @author max
 */
public class JavaClassInitializerElementType extends JavaStubElementType<PsiClassInitializerStub, PsiClassInitializer> {
  public JavaClassInitializerElementType() {
    super("CLASS_INITIALIZER");
  }

  @Nonnull
  @Override
  public ASTNode createCompositeNode() {
    return new ClassInitializerElement();
  }

  @Override
  public PsiClassInitializer createPsi(@Nonnull final PsiClassInitializerStub stub) {
    return getPsiFactory(stub).createClassInitializer(stub);
  }

  @Override
  public PsiClassInitializer createPsi(@Nonnull final ASTNode node) {
    return new PsiClassInitializerImpl(node);
  }

  @Override
  public PsiClassInitializerStub createStub(final LighterAST tree,
                                            final LighterASTNode node,
                                            final StubElement parentStub) {
    return new PsiClassInitializerStubImpl(parentStub);
  }

  @Override
  public void serialize(@Nonnull final PsiClassInitializerStub stub, @Nonnull final StubOutputStream dataStream) throws IOException {
  }

  @Nonnull
  @Override
  public PsiClassInitializerStub deserialize(@Nonnull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PsiClassInitializerStubImpl(parentStub);
  }

  @Override
  public void indexStub(@Nonnull final PsiClassInitializerStub stub, @Nonnull final IndexSink sink) {
  }
}
