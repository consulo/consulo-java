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
package com.intellij.java.language.impl.psi.impl.java.stubs.impl;

import static consulo.util.lang.ObjectUtil.notNull;

import com.intellij.java.language.psi.PsiRequiresStatement;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiRequiresStatementStub;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;

public class PsiRequiresStatementStubImpl extends StubBase<PsiRequiresStatement> implements PsiRequiresStatementStub
{
	private final String myModuleName;

	public PsiRequiresStatementStubImpl(StubElement parent, String refText)
	{
		super(parent, JavaStubElementTypes.REQUIRES_STATEMENT);
		myModuleName = notNull(refText, "");
	}

	@Override
	public String getModuleName()
	{
		return myModuleName;
	}

	@Override
	public String toString()
	{
		return "PsiRequiresStatementStub:" + myModuleName;
	}
}