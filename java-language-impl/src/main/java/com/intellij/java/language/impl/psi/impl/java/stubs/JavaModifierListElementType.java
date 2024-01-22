/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.java.language.impl.psi.impl.cache.RecordUtil;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiModifierListStubImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiModifierListImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.ModifierListElement;
import com.intellij.java.language.psi.PsiModifierList;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;

import jakarta.annotation.Nonnull;
import java.io.IOException;

import static com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType.*;

/**
 * @author max
 */
public class JavaModifierListElementType extends JavaStubElementType<PsiModifierListStub, PsiModifierList> {
  public JavaModifierListElementType() {
    super("MODIFIER_LIST");
  }

  @Nonnull
  @Override
  public ASTNode createCompositeNode() {
    return new ModifierListElement();
  }

  @Override
  public PsiModifierList createPsi(@Nonnull final PsiModifierListStub stub) {
    return getPsiFactory(stub).createModifierList(stub);
  }

  @Override
  public PsiModifierList createPsi(@Nonnull final ASTNode node) {
    return new PsiModifierListImpl(node);
  }

  @Override
  public PsiModifierListStub createStub(final LighterAST tree, final LighterASTNode node, final StubElement parentStub) {
    return new PsiModifierListStubImpl(parentStub, RecordUtil.packModifierList(tree, node));
  }

  @Override
  public void serialize(@Nonnull final PsiModifierListStub stub, @Nonnull final StubOutputStream dataStream) throws IOException {
    dataStream.writeVarInt(stub.getModifiersMask());
  }

  @Override
  public boolean shouldCreateStub(final ASTNode node) {
    final IElementType parentType = node.getTreeParent().getElementType();
    return shouldCreateStub(parentType);
  }

  @Override
  public boolean shouldCreateStub(final LighterAST tree, final LighterASTNode node, final StubElement parentStub) {
    final LighterASTNode parent = tree.getParent(node);
    final IElementType parentType = parent != null ? parent.getTokenType() : null;
    return shouldCreateStub(parentType);
  }

  private static boolean shouldCreateStub(IElementType parentType) {
    return parentType != null && parentType != LOCAL_VARIABLE && parentType != RESOURCE_VARIABLE && parentType != RESOURCE_LIST;
  }

  @Nonnull
  @Override
  public PsiModifierListStub deserialize(@Nonnull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    return new PsiModifierListStubImpl(parentStub, dataStream.readVarInt());
  }

  @Override
  public void indexStub(@Nonnull final PsiModifierListStub stub, @Nonnull final IndexSink sink) {
  }
}
