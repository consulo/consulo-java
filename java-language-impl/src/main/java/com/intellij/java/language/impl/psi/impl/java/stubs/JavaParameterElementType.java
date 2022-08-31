// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.impl.psi.impl.cache.RecordUtil;
import com.intellij.java.language.impl.psi.impl.cache.TypeInfo;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiParameterStubImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiParameterImpl;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.java.language.impl.psi.impl.source.tree.java.ParameterElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import javax.annotation.Nonnull;

import java.io.IOException;

/**
 * @author max
 */
public class JavaParameterElementType extends JavaStubElementType<PsiParameterStub, PsiParameter>
{
	public JavaParameterElementType()
	{
		super("PARAMETER");
	}

	@Nonnull
	@Override
	public ASTNode createCompositeNode()
	{
		return new ParameterElement(JavaElementType.PARAMETER);
	}

	@Override
	public PsiParameter createPsi(@Nonnull PsiParameterStub stub)
	{
		return getPsiFactory(stub).createParameter(stub);
	}

	@Override
	public PsiParameter createPsi(@Nonnull ASTNode node)
	{
		return new PsiParameterImpl(node);
	}

	@Nonnull
	@Override
	public PsiParameterStub createStub(@Nonnull LighterAST tree, @Nonnull LighterASTNode node, @Nonnull StubElement parentStub)
	{
		TypeInfo typeInfo = TypeInfo.create(tree, node, parentStub);
		LighterASTNode id = LightTreeUtil.requiredChildOfType(tree, node, JavaTokenType.IDENTIFIER);
		String name = RecordUtil.intern(tree.getCharTable(), id);
		return new PsiParameterStubImpl(parentStub, name, typeInfo, typeInfo.isEllipsis, false);
	}

	@Override
	public void serialize(@Nonnull PsiParameterStub stub, @Nonnull StubOutputStream dataStream) throws IOException
	{
		dataStream.writeName(stub.getName());
		TypeInfo.writeTYPE(dataStream, stub.getType());
		dataStream.writeByte(((PsiParameterStubImpl) stub).getFlags());
	}

	@Nonnull
	@Override
	public PsiParameterStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException
	{
		String name = dataStream.readNameString();
		if(name == null)
			throw new IOException("corrupted indices");
		TypeInfo type = TypeInfo.readTYPE(dataStream);
		byte flags = dataStream.readByte();
		return new PsiParameterStubImpl(parentStub, name, type, flags);
	}

	@Override
	public void indexStub(@Nonnull PsiParameterStub stub, @Nonnull IndexSink sink)
	{
	}
}