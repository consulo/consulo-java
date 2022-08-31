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
package com.intellij.psi.impl.java.stubs;

import java.io.IOException;

import javax.annotation.Nonnull;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.java.language.psi.PsiProvidesStatement;
import com.intellij.psi.impl.java.stubs.impl.PsiProvidesStatementStubImpl;
import com.intellij.psi.impl.source.PsiProvidesStatementImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;

public class JavaProvidesStatementElementType extends JavaStubElementType<PsiProvidesStatementStub, PsiProvidesStatement>
{
	public JavaProvidesStatementElementType()
	{
		super("PROVIDES_STATEMENT");
	}

	@Nonnull
	@Override
	public ASTNode createCompositeNode()
	{
		return new CompositeElement(this);
	}

	@Override
	public PsiProvidesStatement createPsi(@Nonnull PsiProvidesStatementStub stub)
	{
		return getPsiFactory(stub).createProvidesStatement(stub);
	}

	@Override
	public PsiProvidesStatement createPsi(@Nonnull ASTNode node)
	{
		return new PsiProvidesStatementImpl(node);
	}

	@Override
	public PsiProvidesStatementStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub)
	{
		LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, node, JavaElementType.JAVA_CODE_REFERENCE);
		String refText = ref != null ? JavaSourceUtil.getReferenceText(tree, ref) : null;
		return new PsiProvidesStatementStubImpl(parentStub, refText);
	}

	@Override
	public void serialize(@Nonnull PsiProvidesStatementStub stub, @Nonnull StubOutputStream dataStream) throws IOException
	{
		dataStream.writeName(stub.getInterface());
	}

	@Nonnull
	@Override
	public PsiProvidesStatementStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException
	{
		return new PsiProvidesStatementStubImpl(parentStub, StringRef.toString(dataStream.readName()));
	}

	@Override
	public void indexStub(@Nonnull PsiProvidesStatementStub stub, @Nonnull IndexSink sink)
	{
	}
}