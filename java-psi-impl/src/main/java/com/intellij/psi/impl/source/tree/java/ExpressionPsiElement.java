/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;

import javax.annotation.Nonnull;

/**
 * @author max
 */
public class ExpressionPsiElement extends CompositePsiElement
{
	@SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
	private final int myHC = CompositePsiElement.ourHC++;

	public ExpressionPsiElement(final IElementType type)
	{
		super(type);
	}

	@Override
	public void replaceChildInternal(@Nonnull ASTNode child, @Nonnull TreeElement newElement)
	{
		super.replaceChildInternal(child, JavaSourceUtil.addParenthToReplacedChild(child, newElement, getManager()));
	}

	@Override
	public final int hashCode()
	{
		return myHC;
	}
}
