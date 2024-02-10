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
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import consulo.language.ast.ASTNode;
import consulo.language.impl.psi.CompositePsiElement;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSourceUtil;
import consulo.language.impl.ast.TreeElement;
import consulo.language.ast.IElementType;

import jakarta.annotation.Nonnull;

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
