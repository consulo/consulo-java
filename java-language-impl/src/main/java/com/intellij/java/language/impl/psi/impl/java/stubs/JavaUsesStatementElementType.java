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

import java.io.IOException;


import consulo.language.ast.ASTNode;
import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import com.intellij.java.language.psi.PsiUsesStatement;
import com.intellij.java.language.impl.psi.impl.java.stubs.impl.PsiUsesStatementStubImpl;
import com.intellij.java.language.impl.psi.impl.source.PsiUsesStatementImpl;
import consulo.language.impl.ast.CompositeElement;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSourceUtil;
import consulo.language.ast.LightTreeUtil;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.index.io.StringRef;

public class JavaUsesStatementElementType extends JavaStubElementType<PsiUsesStatementStub, PsiUsesStatement>
{
	public JavaUsesStatementElementType()
	{
		super("USES_STATEMENT");
	}

	@Override
	public ASTNode createCompositeNode()
	{
		return new CompositeElement(this);
	}

	@Override
	public PsiUsesStatement createPsi(PsiUsesStatementStub stub)
	{
		return getPsiFactory(stub).createUsesStatement(stub);
	}

	@Override
	public PsiUsesStatement createPsi(ASTNode node)
	{
		return new PsiUsesStatementImpl(node);
	}

	@Override
	public PsiUsesStatementStub createStub(LighterAST tree, LighterASTNode node, StubElement parentStub)
	{
		LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, node, JavaElementType.JAVA_CODE_REFERENCE);
		String refText = ref != null ? JavaSourceUtil.getReferenceText(tree, ref) : null;
		return new PsiUsesStatementStubImpl(parentStub, refText);
	}

	@Override
	public void serialize(PsiUsesStatementStub stub, StubOutputStream dataStream) throws IOException
	{
		dataStream.writeName(stub.getClassName());
	}

	@Override
	public PsiUsesStatementStub deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException
	{
		return new PsiUsesStatementStubImpl(parentStub, StringRef.toString(dataStream.readName()));
	}

	@Override
	public void indexStub(PsiUsesStatementStub stub, IndexSink sink)
	{
	}
}