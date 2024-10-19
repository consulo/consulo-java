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
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.parser.JavaParser;
import com.intellij.java.language.impl.parser.JavaParserUtil;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.JavaFileElement;
import consulo.index.io.StringRef;
import consulo.language.ast.ASTNode;
import consulo.language.ast.LighterASTNode;
import consulo.language.parser.PsiBuilder;
import consulo.language.psi.stub.*;
import consulo.language.util.FlyweightCapableTreeStructure;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import java.io.IOException;

/**
 * @author max
 */
public class JavaFileElementType extends ILightStubFileElementType<PsiJavaFileStub> {
  public static final int STUB_VERSION = 53;

  public JavaFileElementType() {
    super("java.FILE", JavaLanguage.INSTANCE);
  }

  @Override
  public LightStubBuilder getBuilder() {
    return new JavaLightStubBuilder();
  }

  @Override
  public int getStubVersion() {
    return STUB_VERSION;
  }

  @Override
  public boolean shouldBuildStubFor(final VirtualFile file) {
    return isInSourceContent(file);
  }

  public static boolean isInSourceContent(@Nonnull VirtualFile file) {
    final VirtualFile dir = file.getParent();
    return dir == null || dir.getUserData(LanguageLevel.KEY) != null;
  }

  @Override
  public ASTNode createNode(final CharSequence text) {
    return new JavaFileElement(text);
  }

  @Override
  public FlyweightCapableTreeStructure<LighterASTNode> parseContentsLight(final ASTNode chameleon) {
    final PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
    doParse(builder);
    return builder.getLightTree();
  }

  @Override
  public ASTNode parseContents(@Nonnull final ASTNode chameleon) {
    final PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
    doParse(builder);
    return builder.getTreeBuilt().getFirstChildNode();
  }

  private void doParse(final PsiBuilder builder) {
    final PsiBuilder.Marker root = builder.mark();
    JavaParser.INSTANCE.getFileParser().parse(builder);
    root.done(this);
  }

  @Nonnull
  @Override
  public String getExternalId() {
    return "java.FILE";
  }

  @Override
  public void serialize(@Nonnull PsiJavaFileStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
    dataStream.writeBoolean(stub.isCompiled());
    LanguageLevel level = stub.getLanguageLevel();
    dataStream.writeByte(level != null ? level.ordinal() : -1);
    dataStream.writeName(stub.getPackageName());
  }

  @Nonnull
  @Override
  public PsiJavaFileStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    boolean compiled = dataStream.readBoolean();
    int level = dataStream.readByte();
    StringRef packageName = dataStream.readName();
    return new PsiJavaFileStubImpl(null, StringRef.toString(packageName), level >= 0 ? LanguageLevel.values()[level] : null, compiled);
  }

  @Override
  public void indexStub(@Nonnull PsiJavaFileStub stub, @Nonnull IndexSink sink) {
  }
}