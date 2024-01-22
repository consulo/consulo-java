/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiPackageAccessibilityStatementStubImpl;
import com.intellij.java.language.impl.psi.impl.source.PackageAccessibilityStatementElement;
import com.intellij.java.language.impl.psi.impl.source.PsiPackageAccessibilityStatementImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.java.language.psi.PsiPackageAccessibilityStatement;
import consulo.index.io.StringRef;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.util.collection.SmartList;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

public class JavaPackageAccessibilityStatementElementType extends JavaStubElementType<PsiPackageAccessibilityStatementStub, PsiPackageAccessibilityStatement> {
  public JavaPackageAccessibilityStatementElementType(@jakarta.annotation.Nonnull String debugName) {
    super(debugName);
  }

  @Override
  public PsiPackageAccessibilityStatement createPsi(@jakarta.annotation.Nonnull PsiPackageAccessibilityStatementStub stub) {
    return getPsiFactory(stub).createPackageAccessibilityStatement(stub);
  }

  @Override
  public PsiPackageAccessibilityStatement createPsi(@Nonnull ASTNode node) {
    return new PsiPackageAccessibilityStatementImpl(node);
  }

  @Nonnull
  @Override
  public ASTNode createCompositeNode() {
    return new PackageAccessibilityStatementElement(this);
  }

  @Override
  public PsiPackageAccessibilityStatementStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    String refText = null;
    List<String> to = new SmartList<>();

    for (LighterASTNode child : tree.getChildren(node)) {
      IElementType type = child.getTokenType();
      if (type == JavaElementType.JAVA_CODE_REFERENCE) {
        refText = JavaSourceUtil.getReferenceText(tree, child);
      } else if (type == JavaElementType.MODULE_REFERENCE) {
        to.add(JavaSourceUtil.getReferenceText(tree, child));
      }
    }

    return new PsiPackageAccessibilityStatementStubImpl(parentStub, this, refText, to);
  }

  @Override
  public void serialize(@Nonnull PsiPackageAccessibilityStatementStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getPackageName());
    dataStream.writeUTFFast(StringUtil.join(stub.getTargets(), "/"));
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiPackageAccessibilityStatementStub deserialize(@jakarta.annotation.Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String packageName = StringRef.toString(dataStream.readName());
    List<String> targets = StringUtil.split(dataStream.readUTFFast(), "/");
    return new PsiPackageAccessibilityStatementStubImpl(parentStub, this, packageName, targets);
  }

  @Override
  public void indexStub(@jakarta.annotation.Nonnull PsiPackageAccessibilityStatementStub stub, @Nonnull IndexSink sink) {
  }

  @Nonnull
  public static PsiPackageAccessibilityStatement.Role typeToRole(@Nonnull IElementType type) {
    if (type == JavaElementType.EXPORTS_STATEMENT) {
      return PsiPackageAccessibilityStatement.Role.EXPORTS;
    }
    if (type == JavaElementType.OPENS_STATEMENT) {
      return PsiPackageAccessibilityStatement.Role.OPENS;
    }
    throw new IllegalArgumentException("Unknown type: " + type);
  }
}