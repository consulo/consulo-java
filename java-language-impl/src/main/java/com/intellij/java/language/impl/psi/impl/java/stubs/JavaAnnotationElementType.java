/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import consulo.util.lang.StringUtil;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiNameHelper;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiAnnotationStubImpl;
import com.intellij.java.language.impl.psi.impl.java.stubs.index.JavaStubIndexKeys;
import consulo.language.ast.LightTreeUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.java.AnnotationElement;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiAnnotationImpl;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import jakarta.annotation.Nonnull;

import java.io.IOException;

/**
 * @author max
 */
public class JavaAnnotationElementType extends JavaStubElementType<PsiAnnotationStub, PsiAnnotation> {
  public JavaAnnotationElementType() {
    super("ANNOTATION");
  }

  @Nonnull
  @Override
  public ASTNode createCompositeNode() {
    return new AnnotationElement();
  }

  @Override
  public PsiAnnotation createPsi(@Nonnull PsiAnnotationStub stub) {
    return getPsiFactory(stub).createAnnotation(stub);
  }

  @Override
  public PsiAnnotation createPsi(@Nonnull ASTNode node) {
    return new PsiAnnotationImpl(node);
  }

  @Override
  public PsiAnnotationStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    String text = LightTreeUtil.toFilteredString(tree, node, null);
    return new PsiAnnotationStubImpl(parentStub, text);
  }

  @Override
  public void serialize(@Nonnull PsiAnnotationStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
    dataStream.writeUTFFast(stub.getText());
  }

  @Nonnull
  @Override
  public PsiAnnotationStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new PsiAnnotationStubImpl(parentStub, dataStream.readUTFFast());
  }

  @Override
  public void indexStub(@Nonnull PsiAnnotationStub stub, @Nonnull IndexSink sink) {
    String shortName = getReferenceShortName(stub.getText());
    if (!StringUtil.isEmptyOrSpaces(shortName)) {
      sink.occurrence(JavaStubIndexKeys.ANNOTATIONS, shortName);
    }
  }

  private static String getReferenceShortName(String annotationText) {
    int index = annotationText.indexOf('(');
    if (index >= 0) annotationText = annotationText.substring(0, index);
    return PsiNameHelper.getShortClassName(annotationText);
  }
}
