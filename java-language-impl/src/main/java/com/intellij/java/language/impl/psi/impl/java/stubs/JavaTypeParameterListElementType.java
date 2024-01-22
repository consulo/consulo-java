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
import com.intellij.java.language.psi.PsiTypeParameterList;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiTypeParameterListStubImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiTypeParameterListImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.TypeParameterListElement;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import jakarta.annotation.Nonnull;

import java.io.IOException;

/**
 * @author max
 */
public class JavaTypeParameterListElementType extends JavaStubElementType<PsiTypeParameterListStub, PsiTypeParameterList> {
  public JavaTypeParameterListElementType() {
    super("TYPE_PARAMETER_LIST", true);
  }

  @Nonnull
  @Override
  public ASTNode createCompositeNode() {
    return new TypeParameterListElement();
  }

  @Override
  public PsiTypeParameterList createPsi(@jakarta.annotation.Nonnull final PsiTypeParameterListStub stub) {
    return getPsiFactory(stub).createTypeParameterList(stub);
  }

  @Override
  public PsiTypeParameterList createPsi(@jakarta.annotation.Nonnull final ASTNode node) {
    return new PsiTypeParameterListImpl(node);
  }

  @Override
  public PsiTypeParameterListStub createStub(final LighterAST tree,
                                             final LighterASTNode node,
                                             final StubElement parentStub) {
    return new PsiTypeParameterListStubImpl(parentStub);
  }

  @Override
  public void serialize(@Nonnull final PsiTypeParameterListStub stub, @Nonnull final StubOutputStream dataStream) throws IOException {
  }

  @Nonnull
  @Override
  public PsiTypeParameterListStub deserialize(@Nonnull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PsiTypeParameterListStubImpl(parentStub);
  }

  @Override
  public void indexStub(@Nonnull final PsiTypeParameterListStub stub, @Nonnull final IndexSink sink) {
  }
}
