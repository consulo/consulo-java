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
package com.intellij.java.language.impl.psi.impl.source;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import consulo.language.ast.ASTNode;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import consulo.language.impl.ast.CompositeElement;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import consulo.language.ast.IElementType;

/**
 * @author Pavel.Dolgov
 */
public class PackageAccessibilityStatementElement extends CompositeElement
{
	public PackageAccessibilityStatementElement(@Nonnull IElementType type)
	{
		super(type);
	}

	@Override
	public void deleteChildInternal(@Nonnull ASTNode child)
	{
		if(child.getElementType() == JavaElementType.MODULE_REFERENCE)
		{
			ASTNode comma = findNearestComma(child);
			if(comma != null)
			{
				super.deleteChildInternal(comma);
			}
			else
			{
				ASTNode toKeyword = findChildByType(JavaTokenType.TO_KEYWORD);
				if(toKeyword != null)
				{
					super.deleteChildInternal(toKeyword);
				}
			}
		}
		super.deleteChildInternal(child);
	}

	@Nullable
	private static ASTNode findNearestComma(@Nonnull ASTNode child)
	{
		ASTNode next = PsiImplUtil.skipWhitespaceAndComments(child.getTreeNext());
		if(next != null && next.getElementType() == JavaTokenType.COMMA)
		{
			return next;
		}
		else
		{
			ASTNode prev = PsiImplUtil.skipWhitespaceAndCommentsBack(child.getTreePrev());
			if(prev != null && prev.getElementType() == JavaTokenType.COMMA)
			{
				return prev;
			}
		}
		return null;
	}
}