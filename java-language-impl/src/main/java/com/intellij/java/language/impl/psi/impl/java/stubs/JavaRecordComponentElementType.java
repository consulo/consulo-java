// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.java.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiRecordComponent;
import com.intellij.java.language.impl.psi.impl.cache.RecordUtil;
import com.intellij.java.language.impl.psi.impl.cache.TypeInfo;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiRecordComponentStubImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiRecordComponentImpl;
import consulo.language.impl.ast.CompositeElement;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import consulo.language.ast.LightTreeUtil;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import jakarta.annotation.Nonnull;

import java.io.IOException;

public class JavaRecordComponentElementType extends JavaStubElementType<PsiRecordComponentStub, PsiRecordComponent>
{
	public JavaRecordComponentElementType()
	{
		super("RECORD_COMPONENT");
	}

	@jakarta.annotation.Nonnull
	@Override
	public ASTNode createCompositeNode()
	{
		return new CompositeElement(this);
	}

	@Override
	public void serialize(@Nonnull PsiRecordComponentStub stub, @jakarta.annotation.Nonnull StubOutputStream dataStream) throws IOException
	{
		dataStream.writeName(stub.getName());
		TypeInfo.writeTYPE(dataStream, stub.getType());
		dataStream.writeByte(((PsiRecordComponentStubImpl) stub).getFlags());
	}

	@Nonnull
	@Override
	public PsiRecordComponentStub deserialize(@jakarta.annotation.Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException
	{
		String name = dataStream.readNameString();
		TypeInfo type = TypeInfo.readTYPE(dataStream);
		byte flags = dataStream.readByte();
		return new PsiRecordComponentStubImpl(parentStub, name, type, flags);
	}

	@Override
	public void indexStub(@jakarta.annotation.Nonnull PsiRecordComponentStub stub, @jakarta.annotation.Nonnull IndexSink sink)
	{

	}

	@Override
	public PsiRecordComponent createPsi(@jakarta.annotation.Nonnull PsiRecordComponentStub stub)
	{
		return getPsiFactory(stub).createRecordComponent(stub);
	}


	@Override
	public PsiRecordComponent createPsi(@jakarta.annotation.Nonnull ASTNode node)
	{
		return new PsiRecordComponentImpl(node);
	}

	@jakarta.annotation.Nonnull
	@Override
	public PsiRecordComponentStub createStub(@Nonnull LighterAST tree, @jakarta.annotation.Nonnull LighterASTNode node, @jakarta.annotation.Nonnull StubElement parentStub)
	{
		TypeInfo typeInfo = TypeInfo.create(tree, node, parentStub);
		LighterASTNode id = LightTreeUtil.requiredChildOfType(tree, node, JavaTokenType.IDENTIFIER);
		String name = RecordUtil.intern(tree.getCharTable(), id);

		LighterASTNode modifierList = LightTreeUtil.firstChildOfType(tree, node, JavaElementType.MODIFIER_LIST);
		boolean hasDeprecatedAnnotation = modifierList != null && RecordUtil.isDeprecatedByAnnotation(tree, modifierList);
		return new PsiRecordComponentStubImpl(parentStub, name, typeInfo, typeInfo.isEllipsis, hasDeprecatedAnnotation);
	}
}