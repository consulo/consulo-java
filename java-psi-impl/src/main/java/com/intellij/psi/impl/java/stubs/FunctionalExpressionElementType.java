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
package com.intellij.psi.impl.java.stubs;

import java.io.IOException;

import javax.annotation.Nonnull;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiFunctionalExpression;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.io.StringRef;

public abstract class FunctionalExpressionElementType<T extends PsiFunctionalExpression> extends JavaStubElementType<FunctionalExpressionStub<T>, T>
{
	public FunctionalExpressionElementType(String debugName)
	{
		super(debugName);
	}

	@Override
	public void serialize(@Nonnull FunctionalExpressionStub<T> stub, @Nonnull StubOutputStream dataStream) throws IOException
	{
		dataStream.writeName(stub.getPresentableText());
	}

	@Nonnull
	@Override
	public FunctionalExpressionStub<T> deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException
	{
		return new FunctionalExpressionStub<T>(parentStub, this, StringRef.toString(dataStream.readName()));
	}

	@Override
	public void indexStub(@Nonnull FunctionalExpressionStub<T> stub, @Nonnull IndexSink sink)
	{
	}

	@Override
	public FunctionalExpressionStub<T> createStub(LighterAST tree, LighterASTNode funExpr, StubElement parentStub)
	{
		return new FunctionalExpressionStub<T>(parentStub, this, getPresentableText(tree, funExpr));
	}

	@Nonnull
	protected abstract String getPresentableText(@Nonnull LighterAST tree, @Nonnull LighterASTNode funExpr);
}
