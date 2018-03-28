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
package com.intellij.psi.impl.compiled;

import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.tree.IElementType;

class ClsDocCommentImpl extends ClsElementImpl implements PsiDocComment, JavaTokenType, PsiJavaToken
{
	private final PsiDocCommentOwner myParent;
	private final PsiDocTag[] myTags;

	ClsDocCommentImpl(@Nonnull PsiDocCommentOwner parent)
	{
		myParent = parent;
		myTags = new PsiDocTag[]{new ClsDocTagImpl(this, "@deprecated")};
	}

	@Override
	public void appendMirrorText(final int indentLevel, @Nonnull final StringBuilder buffer)
	{
		buffer.append("/**");
		for(PsiDocTag tag : getTags())
		{
			goNextLine(indentLevel + 1, buffer);
			buffer.append("* ");
			buffer.append(tag.getText());
		}
		goNextLine(indentLevel + 1, buffer);
		buffer.append("*/");
	}

	@Override
	public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException
	{
		setMirrorCheckingType(element, JavaDocElementType.DOC_COMMENT);
	}

	@Override
	@Nonnull
	public PsiElement[] getChildren()
	{
		return getTags();
	}

	@Override
	public PsiElement getParent()
	{
		return myParent;
	}

	@Override
	public PsiJavaDocumentedElement getOwner()
	{
		return PsiImplUtil.findDocCommentOwner(this);
	}

	@Override
	@Nonnull
	public PsiElement[] getDescriptionElements()
	{
		return PsiElement.EMPTY_ARRAY;
	}

	@Override
	@Nonnull
	public PsiDocTag[] getTags()
	{
		return myTags;
	}

	@Override
	public PsiDocTag findTagByName(@NonNls String name)
	{
		return name.equals("deprecated") ? getTags()[0] : null;
	}

	@Override
	@Nonnull
	public PsiDocTag[] findTagsByName(@NonNls String name)
	{
		return name.equals("deprecated") ? getTags() : PsiDocTag.EMPTY_ARRAY;
	}

	@Override
	public IElementType getTokenType()
	{
		return JavaDocElementType.DOC_COMMENT;
	}

	@Override
	public void accept(@Nonnull PsiElementVisitor visitor)
	{
		if(visitor instanceof JavaElementVisitor)
		{
			((JavaElementVisitor) visitor).visitDocComment(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}
}
