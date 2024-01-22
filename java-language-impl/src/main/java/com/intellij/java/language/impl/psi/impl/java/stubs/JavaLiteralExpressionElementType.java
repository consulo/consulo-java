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

import java.io.IOException;

import jakarta.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.impl.psi.impl.cache.RecordUtil;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiLiteralStub;
import consulo.language.impl.ast.CompositeElement;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;

/**
 * @author peter
 */
public class JavaLiteralExpressionElementType extends JavaStubElementType<PsiLiteralStub, PsiLiteralExpression>
{
	public JavaLiteralExpressionElementType()
	{
		super("LITERAL_EXPRESSION");
	}

	@Override
	public PsiLiteralExpression createPsi(@Nonnull ASTNode node)
	{
		return new PsiLiteralExpressionImpl(node);
	}

	@Override
	public PsiLiteralStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub)
	{
		return new PsiLiteralStub(parentStub, RecordUtil.intern(tree.getCharTable(), tree.getChildren(node).get(0)));
	}

	@Override
	public PsiLiteralExpression createPsi(@jakarta.annotation.Nonnull PsiLiteralStub stub)
	{
		return new PsiLiteralExpressionImpl(stub);
	}

	@Override
	public void serialize(@Nonnull PsiLiteralStub stub, @jakarta.annotation.Nonnull StubOutputStream dataStream) throws IOException
	{
		dataStream.writeUTFFast(stub.getLiteralText());
	}

	@jakarta.annotation.Nonnull
	@Override
	public PsiLiteralStub deserialize(@jakarta.annotation.Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException
	{
		return new PsiLiteralStub(parentStub, dataStream.readUTFFast());
	}

	@Override
	public void indexStub(@jakarta.annotation.Nonnull PsiLiteralStub stub, @jakarta.annotation.Nonnull IndexSink sink)
	{
	}

	@jakarta.annotation.Nonnull
	@Override
	public ASTNode createCompositeNode()
	{
		return new CompositeElement(this);
	}

	@Override
	public boolean shouldCreateStub(LighterAST tree, LighterASTNode node, StubElement parentStub)
	{
		LighterASTNode parent = tree.getParent(node);
		return parent != null && parent.getTokenType() == JavaStubElementTypes.NAME_VALUE_PAIR;
	}

	@Override
	public boolean shouldCreateStub(ASTNode node)
	{
		return node.getTreeParent().getElementType() == JavaStubElementTypes.NAME_VALUE_PAIR;
	}
}