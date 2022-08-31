// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.java.language.psi.PsiRecordHeader;
import com.intellij.psi.impl.java.stubs.impl.PsiRecordHeaderStubImpl;
import com.intellij.psi.impl.source.PsiRecordHeaderImpl;
import com.intellij.psi.impl.source.tree.java.RecordHeaderElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import javax.annotation.Nonnull;

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
	public void serialize(@Nonnull PsiRecordHeaderStub stub, @Nonnull StubOutputStream dataStream) throws IOException
	{
	}

	@Nonnull
	@Override
	public PsiRecordHeaderStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException
	{
		return new PsiRecordHeaderStubImpl(parentStub);
	}

	@Override
	public void indexStub(@Nonnull PsiRecordHeaderStub stub, @Nonnull IndexSink sink)
	{

	}

	@Override
	public PsiRecordHeader createPsi(@Nonnull PsiRecordHeaderStub stub)
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
	public PsiRecordHeaderStub createStub(@Nonnull LighterAST tree, @Nonnull LighterASTNode node, @Nonnull StubElement parentStub)
	{
		return new PsiRecordHeaderStubImpl(parentStub);
	}
}