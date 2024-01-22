/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.java.language.psi.PsiAnnotationParameterList;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiAnnotationParameterListStubImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.AnnotationParamListElement;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiAnnotationParamListImpl;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import jakarta.annotation.Nonnull;

import java.io.IOException;

/**
 * @author Dmitry Avdeev
 *         Date: 7/27/12
 */
public class JavaAnnotationParameterListType extends JavaStubElementType<PsiAnnotationParameterListStub, PsiAnnotationParameterList> {

  protected JavaAnnotationParameterListType() {
    super("ANNOTATION_PARAMETER_LIST", true);
  }

  @Override
  public PsiAnnotationParameterList createPsi(@jakarta.annotation.Nonnull ASTNode node) {
    return new PsiAnnotationParamListImpl(node);
  }

  @jakarta.annotation.Nonnull
  @Override
  public ASTNode createCompositeNode() {
    return new AnnotationParamListElement();
  }

  @Override
  public PsiAnnotationParameterListStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    return new PsiAnnotationParameterListStubImpl(parentStub);
  }

  @Override
  public PsiAnnotationParameterList createPsi(@Nonnull PsiAnnotationParameterListStub stub) {
    return getPsiFactory(stub).createAnnotationParameterList(stub);
  }

  @Override
  public void serialize(@jakarta.annotation.Nonnull PsiAnnotationParameterListStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiAnnotationParameterListStub deserialize(@jakarta.annotation.Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiAnnotationParameterListStubImpl(parentStub);
  }

  @Override
  public void indexStub(@jakarta.annotation.Nonnull PsiAnnotationParameterListStub stub, @jakarta.annotation.Nonnull IndexSink sink) {
  }
}
