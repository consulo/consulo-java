// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiRecordComponent;
import com.intellij.java.language.psi.PsiRecordHeader;
import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiRecordHeaderStub;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PsiRecordHeaderImpl extends JavaStubPsiElement<PsiRecordHeaderStub> implements PsiRecordHeader
{
	public PsiRecordHeaderImpl(@Nonnull PsiRecordHeaderStub stub)
	{
		super(stub, JavaStubElementTypes.RECORD_HEADER);
	}

	public PsiRecordHeaderImpl(@Nonnull ASTNode node)
	{
		super(node);
	}

	@Nullable
	@Override
	public PsiClass getContainingClass()
	{
		PsiElement parent = getParent();
		return parent instanceof PsiClass ? (PsiClass) parent : null;
	}

	@Override
	public void accept(@Nonnull PsiElementVisitor visitor)
	{
		if(visitor instanceof JavaElementVisitor)
		{
			((JavaElementVisitor) visitor).visitRecordHeader(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	@Override
	public String toString()
	{
		return "PsiRecordHeader";
	}

	@Override
	@Nonnull
	public PsiRecordComponent[] getRecordComponents()
	{
		return getStubOrPsiChildren(JavaStubElementTypes.RECORD_COMPONENT, PsiRecordComponent.EMPTY_ARRAY);
	}
}
