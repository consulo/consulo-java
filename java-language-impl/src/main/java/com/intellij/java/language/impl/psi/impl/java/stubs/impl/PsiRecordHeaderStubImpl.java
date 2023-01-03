// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.java.stubs.impl;

import com.intellij.java.language.psi.PsiRecordHeader;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiRecordHeaderStub;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;

public class PsiRecordHeaderStubImpl extends StubBase<PsiRecordHeader> implements PsiRecordHeaderStub
{
	public PsiRecordHeaderStubImpl(final StubElement parent)
	{
		super(parent, JavaStubElementTypes.RECORD_HEADER);
	}

	public String toString()
	{
		return "PsiRecordHeaderStub";
	}
}