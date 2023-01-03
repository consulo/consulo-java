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

import java.io.IOException;

import javax.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import com.intellij.java.language.psi.PsiImportList;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiImportListStubImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiImportListImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.java.ImportListElement;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;

/**
 * @author max
 */
public class JavaImportListElementType extends JavaStubElementType<PsiImportListStub, PsiImportList>
{
	public JavaImportListElementType()
	{
		super("IMPORT_LIST");
	}

	@Nonnull
	@Override
	public ASTNode createCompositeNode()
	{
		return new ImportListElement();
	}

	@Override
	public PsiImportList createPsi(@Nonnull final PsiImportListStub stub)
	{
		return getPsiFactory(stub).createImportList(stub);
	}

	@Override
	public PsiImportList createPsi(@Nonnull final ASTNode node)
	{
		return new PsiImportListImpl(node);
	}

	@Override
	public PsiImportListStub createStub(final LighterAST tree, final LighterASTNode node, final StubElement parentStub)
	{
		return new PsiImportListStubImpl(parentStub);
	}

	@Override
	public void serialize(@Nonnull final PsiImportListStub stub, @Nonnull final StubOutputStream dataStream) throws IOException
	{
	}

	@Nonnull
	@Override
	public PsiImportListStub deserialize(@Nonnull final StubInputStream dataStream, final StubElement parentStub) throws IOException
	{
		return new PsiImportListStubImpl(parentStub);
	}

	@Override
	public void indexStub(@Nonnull final PsiImportListStub stub, @Nonnull final IndexSink sink)
	{
	}
}
