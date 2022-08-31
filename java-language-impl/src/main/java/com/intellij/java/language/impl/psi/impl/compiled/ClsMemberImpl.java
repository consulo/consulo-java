/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.compiled;

import javax.annotation.Nonnull;

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.java.language.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiMemberStub;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;

public abstract class ClsMemberImpl<T extends PsiMemberStub> extends ClsRepositoryPsiElement<T> implements PsiDocCommentOwner, PsiNameIdentifierOwner
{
	private final NotNullLazyValue<PsiDocComment> myDocComment;
	private final NotNullLazyValue<PsiIdentifier> myNameIdentifier;

	protected ClsMemberImpl(T stub)
	{
		super(stub);
		myDocComment = !stub.isDeprecated() ? null : new AtomicNotNullLazyValue<PsiDocComment>()
		{
			@Nonnull
			@Override
			protected PsiDocComment compute()
			{
				return new ClsDocCommentImpl(ClsMemberImpl.this);
			}
		};
		myNameIdentifier = new AtomicNotNullLazyValue<PsiIdentifier>()
		{
			@Nonnull
			@Override
			protected PsiIdentifier compute()
			{
				return new ClsIdentifierImpl(ClsMemberImpl.this, getName());
			}
		};
	}

	@Override
	public PsiDocComment getDocComment()
	{
		return myDocComment != null ? myDocComment.getValue() : null;
	}

	@Override
	@Nonnull
	public PsiIdentifier getNameIdentifier()
	{
		return myNameIdentifier.getValue();
	}

	@Override
	@Nonnull
	public String getName()
	{
		//noinspection ConstantConditions
		return getStub().getName();
	}

	@Override
	public PsiElement setName(@Nonnull String name) throws IncorrectOperationException
	{
		PsiImplUtil.setName(getNameIdentifier(), name);
		return this;
	}
}
