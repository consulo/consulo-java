// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.java.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import com.intellij.java.language.psi.PsiRecordHeader;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiRecordHeaderStubImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiRecordHeaderImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.RecordHeaderElement;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import jakarta.annotation.Nonnull;

import java.io.IOException;

public class JavaRecordHeaderElementType extends JavaStubElementType<PsiRecordHeaderStub, PsiRecordHeader>
{
	public JavaRecordHeaderElementType()
	{
		super("RECORD_HEADER");
	}

	@Nonnull
	@Override
	public ASTNode createCompositeNode()
	{
		return new RecordHeaderElement();
	}

	@Override
	public void serialize(@jakarta.annotation.Nonnull PsiRecordHeaderStub stub, @jakarta.annotation.Nonnull StubOutputStream dataStream) throws IOException
	{
	}

	@Nonnull
	@Override
	public PsiRecordHeaderStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException
	{
		return new PsiRecordHeaderStubImpl(parentStub);
	}

	@Override
	public void indexStub(@jakarta.annotation.Nonnull PsiRecordHeaderStub stub, @Nonnull IndexSink sink)
	{

	}

	@Override
	public PsiRecordHeader createPsi(@jakarta.annotation.Nonnull PsiRecordHeaderStub stub)
	{
		return getPsiFactory(stub).createRecordHeader(stub);
	}


	@Override
	public PsiRecordHeader createPsi(@Nonnull ASTNode node)
	{
		return new PsiRecordHeaderImpl(node);
	}

	@Nonnull
	@Override
	public PsiRecordHeaderStub createStub(@jakarta.annotation.Nonnull LighterAST tree, @jakarta.annotation.Nonnull LighterASTNode node, @Nonnull StubElement parentStub)
	{
		return new PsiRecordHeaderStubImpl(parentStub);
	}
}