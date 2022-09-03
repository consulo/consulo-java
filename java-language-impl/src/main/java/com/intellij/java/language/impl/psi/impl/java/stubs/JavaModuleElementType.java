/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiJavaModuleStubImpl;
import com.intellij.java.language.impl.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.java.language.impl.psi.impl.source.PsiJavaModuleImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.java.language.psi.PsiJavaModule;
import consulo.index.io.StringRef;
import consulo.language.ast.ASTNode;
import consulo.language.ast.LightTreeUtil;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import consulo.language.impl.ast.CompositeElement;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;

import javax.annotation.Nonnull;
import java.io.IOException;

public class JavaModuleElementType extends JavaStubElementType<PsiJavaModuleStub, PsiJavaModule> {
  public JavaModuleElementType() {
    super("MODULE");
  }

  @Nonnull
  @Override
  public ASTNode createCompositeNode() {
    return new CompositeElement(this);
  }

  @Override
  public PsiJavaModule createPsi(@Nonnull PsiJavaModuleStub stub) {
    return getPsiFactory(stub).createModule(stub);
  }

  @Override
  public PsiJavaModule createPsi(@Nonnull ASTNode node) {
    return new PsiJavaModuleImpl(node);
  }

  @Override
  public PsiJavaModuleStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    LighterASTNode ref = LightTreeUtil.requiredChildOfType(tree, node, JavaElementType.MODULE_REFERENCE);
    return new PsiJavaModuleStubImpl(parentStub, JavaSourceUtil.getReferenceText(tree, ref));
  }

  @Override
  public void serialize(@Nonnull PsiJavaModuleStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
  }

  @Nonnull
  @Override
  public PsiJavaModuleStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    return new PsiJavaModuleStubImpl(parentStub, name);
  }

  @Override
  public void indexStub(@Nonnull PsiJavaModuleStub stub, @Nonnull IndexSink sink) {
    sink.occurrence(JavaStubIndexKeys.MODULE_NAMES, stub.getName());
  }
}