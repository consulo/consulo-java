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
import consulo.util.lang.StringUtil;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiNameValuePair;
import com.intellij.java.language.impl.psi.impl.cache.RecordUtil;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiNameValuePairStubImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.java.NameValuePairElement;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiNameValuePairImpl;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.index.io.StringRef;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 7/27/12
 */
public class JavaNameValuePairType extends JavaStubElementType<PsiNameValuePairStub, PsiNameValuePair> {

  protected JavaNameValuePairType() {
    super("NAME_VALUE_PAIR", true);
  }

  @Override
  public PsiNameValuePair createPsi(@Nonnull ASTNode node) {
    return new PsiNameValuePairImpl(node);
  }

  @Nonnull
  @Override
  public ASTNode createCompositeNode() {
    return new NameValuePairElement();
  }

  @Override
  public PsiNameValuePairStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    String name = null;
    String value = null;
    List<LighterASTNode> children = tree.getChildren(node);
    for (LighterASTNode child : children) {
      if (child.getTokenType() == JavaTokenType.IDENTIFIER) {
        name = RecordUtil.intern(tree.getCharTable(), child);
      }
      else if (child.getTokenType() == JavaElementType.LITERAL_EXPRESSION) {
        value = RecordUtil.intern(tree.getCharTable(), tree.getChildren(child).get(0));
        value = StringUtil.stripQuotesAroundValue(value);
      }
    }
    return new PsiNameValuePairStubImpl(parentStub, StringRef.fromString(name), StringRef.fromString(value));
  }

  @Override
  public PsiNameValuePair createPsi(@Nonnull PsiNameValuePairStub stub) {
    return getPsiFactory(stub).createNameValuePair(stub);
  }

  @Override
  public void serialize(@Nonnull PsiNameValuePairStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeName(stub.getValue());
  }

  @Nonnull
  @Override
  public PsiNameValuePairStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef name = dataStream.readName();
    return new PsiNameValuePairStubImpl(parentStub, name, dataStream.readName());
  }

  @Override
  public void indexStub(@Nonnull PsiNameValuePairStub stub, @Nonnull IndexSink sink) {
  }
}
