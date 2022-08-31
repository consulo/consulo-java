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

import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.impl.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiLambdaExpressionImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ObjectUtil;

import javax.annotation.Nonnull;
import java.util.List;

public class LambdaExpressionElementType extends FunctionalExpressionElementType<PsiLambdaExpression>
{
	public LambdaExpressionElementType()
	{
		super("LAMBDA_EXPRESSION");
	}

	@Override
	public PsiLambdaExpression createPsi(@Nonnull ASTNode node)
	{
		return new PsiLambdaExpressionImpl(node);
	}

	@Override
	public PsiLambdaExpression createPsi(@Nonnull FunctionalExpressionStub<PsiLambdaExpression> stub)
	{
		return new PsiLambdaExpressionImpl(stub);
	}

	@Nonnull
	@Override
	public ASTNode createCompositeNode()
	{
		return new CompositeElement(this)
		{
			@Override
			public void replaceChildInternal(@Nonnull ASTNode child, @Nonnull TreeElement newElement)
			{
				super.replaceChildInternal(child, JavaSourceUtil.addParenthToReplacedChild(child, newElement, getManager()));
			}

			@Override
			public int getChildRole(ASTNode child)
			{
				final IElementType elType = child.getElementType();
				if(elType == JavaTokenType.ARROW)
				{
					return ChildRole.ARROW;
				}
				else if(elType == JavaElementType.PARAMETER_LIST)
				{
					return ChildRole.PARAMETER_LIST;
				}
				else if(elType == JavaElementType.CODE_BLOCK)
				{
					return ChildRole.LBRACE;
				}
				else
				{
					return ChildRole.EXPRESSION;
				}
			}
		};
	}

	@Nonnull
	@Override
	protected String getPresentableText(@Nonnull LighterAST tree, @Nonnull LighterASTNode funExpr)
	{
		LighterASTNode parameterList = ObjectUtil.notNull(LightTreeUtil.firstChildOfType(tree, funExpr, JavaStubElementTypes.PARAMETER_LIST));
		return getLambdaPresentableText(tree, parameterList);
	}

	private static String getLambdaPresentableText(@Nonnull LighterAST tree, @Nonnull LighterASTNode parameterList)
	{
		StringBuilder buf = new StringBuilder(parameterList.getEndOffset() - parameterList.getStartOffset());
		formatParameterList(tree, parameterList, buf);
		buf.append(" -> {...}");
		return buf.toString();
	}

	private static void formatParameterList(@Nonnull LighterAST tree, @Nonnull LighterASTNode parameterList, StringBuilder buf)
	{
		final List<LighterASTNode> children = tree.getChildren(parameterList);
		boolean isFirstParameter = true;
		boolean appendCloseBracket = false;
		for(final LighterASTNode node : children)
		{
			final IElementType tokenType = node.getTokenType();
			if(tokenType == JavaTokenType.LPARENTH)
			{
				buf.append('(');
				appendCloseBracket = true;
			}
			else if(tokenType == JavaStubElementTypes.PARAMETER)
			{
				if(!isFirstParameter)
				{
					buf.append(", ");
				}
				formatParameter(tree, node, buf);
				if(isFirstParameter)
				{
					isFirstParameter = false;
				}
			}
		}
		if(appendCloseBracket)
		{
			buf.append(')');
		}
	}

	private static void formatParameter(@Nonnull LighterAST tree, @Nonnull LighterASTNode parameter, StringBuilder buf)
	{
		final List<LighterASTNode> children = tree.getChildren(parameter);
		for(LighterASTNode node : children)
		{
			final IElementType tokenType = node.getTokenType();
			if(tokenType == JavaElementType.TYPE)
			{
				formatType(tree, node, buf);
				buf.append(' ');
			}
			else if(tokenType == JavaTokenType.IDENTIFIER)
			{
				buf.append(RecordUtil.intern(tree.getCharTable(), node));
			}
		}
	}

	private static void formatType(LighterAST tree, LighterASTNode typeElement, StringBuilder buf)
	{
		for(LighterASTNode node : tree.getChildren(typeElement))
		{
			final IElementType tokenType = node.getTokenType();
			if(tokenType == JavaElementType.JAVA_CODE_REFERENCE)
			{
				formatCodeReference(tree, node, buf);
			}
			else if(tokenType == JavaElementType.TYPE)
			{
				formatType(tree, node, buf);
			}
			else if(tokenType == JavaTokenType.QUEST)
			{
				buf.append("? ");
			}
			else if(ElementType.KEYWORD_BIT_SET.contains(tokenType))
			{
				buf.append(RecordUtil.intern(tree.getCharTable(), node));
				if(!ElementType.PRIMITIVE_TYPE_BIT_SET.contains(tokenType))
				{
					buf.append(" ");
				}
			}
			else if(tokenType == JavaTokenType.ELLIPSIS)
			{
				buf.append("...");
			}
			else if(tokenType == JavaTokenType.RBRACKET)
			{
				buf.append("]");
			}
			else if(tokenType == JavaTokenType.LBRACKET)
			{
				buf.append("[");
			}
		}
	}

	private static void formatCodeReference(LighterAST tree, LighterASTNode codeRef, StringBuilder buf)
	{
		for(LighterASTNode node : tree.getChildren(codeRef))
		{
			final IElementType tokenType = node.getTokenType();
			if(tokenType == JavaTokenType.IDENTIFIER)
			{
				buf.append(RecordUtil.intern(tree.getCharTable(), node));
			}
			else if(tokenType == JavaElementType.REFERENCE_PARAMETER_LIST)
			{
				formatTypeParameters(tree, node, buf);
			}
		}
	}

	private static void formatTypeParameters(LighterAST tree, LighterASTNode typeParameters, StringBuilder buf)
	{
		final List<LighterASTNode> children = LightTreeUtil.getChildrenOfType(tree, typeParameters, JavaElementType.TYPE);
		if(children.isEmpty())
		{
			return;
		}
		buf.append('<');
		for(int i = 0; i < children.size(); i++)
		{
			LighterASTNode child = children.get(i);
			formatType(tree, child, buf);
			if(i != children.size() - 1)
			{
				buf.append(", ");
			}
		}
		buf.append('>');
	}
}
