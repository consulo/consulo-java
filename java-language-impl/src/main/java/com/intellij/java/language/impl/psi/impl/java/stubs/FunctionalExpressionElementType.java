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


import consulo.language.ast.LighterAST;
import consulo.language.ast.LighterASTNode;
import com.intellij.java.language.psi.PsiFunctionalExpression;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.index.io.StringRef;

public abstract class FunctionalExpressionElementType<T extends PsiFunctionalExpression> extends JavaStubElementType<FunctionalExpressionStub<T>, T>
{
	public FunctionalExpressionElementType(String debugName)
	{
		super(debugName);
	}

	@Override
	public void serialize(FunctionalExpressionStub<T> stub, StubOutputStream dataStream) throws IOException
	{
		dataStream.writeName(stub.getPresentableText());
	}

	@Override
	public FunctionalExpressionStub<T> deserialize(StubInputStream dataStream, StubElement parentStub) throws IOException
	{
		return new FunctionalExpressionStub<T>(parentStub, this, StringRef.toString(dataStream.readName()));
	}

	@Override
	public void indexStub(FunctionalExpressionStub<T> stub, IndexSink sink)
	{
	}

	@Override
	public FunctionalExpressionStub<T> createStub(LighterAST tree, LighterASTNode funExpr, StubElement parentStub)
	{
		return new FunctionalExpressionStub<T>(parentStub, this, getPresentableText(tree, funExpr));
	}

	protected abstract String getPresentableText(LighterAST tree, LighterASTNode funExpr);
}
