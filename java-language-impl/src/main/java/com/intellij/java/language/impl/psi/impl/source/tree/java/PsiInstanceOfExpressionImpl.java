/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.java.language.psi.*;
import com.intellij.lang.ASTNode;
import consulo.logging.Logger;
import com.intellij.psi.*;
import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.impl.psi.scope.PatternResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;

public class PsiInstanceOfExpressionImpl extends ExpressionPsiElement implements PsiInstanceOfExpression, Constants
{
	private static final Logger LOG = Logger.getInstance(PsiInstanceOfExpressionImpl.class);

	public PsiInstanceOfExpressionImpl()
	{
		super(INSTANCE_OF_EXPRESSION);
	}

	@Override
	@Nonnull
	public PsiExpression getOperand()
	{
		return (PsiExpression) findChildByRoleAsPsiElement(ChildRole.OPERAND);
	}

	@Override
	public PsiTypeElement getCheckType()
	{
		return (PsiTypeElement) findChildByRoleAsPsiElement(ChildRole.TYPE);
	}

	@Override
	public PsiType getType()
	{
		return PsiType.BOOLEAN;
	}

	@Nullable
	@Override
	public PsiPattern getPattern()
	{
		return PsiTreeUtil.getChildOfType(this, PsiPattern.class);
	}

	@Override
	public ASTNode findChildByRole(int role)
	{
		LOG.assertTrue(ChildRole.isUnique(role));
		switch(role)
		{
			default:
				return null;

			case ChildRole.OPERAND:
				return findChildByType(EXPRESSION_BIT_SET);

			case ChildRole.INSTANCEOF_KEYWORD:
				return findChildByType(INSTANCEOF_KEYWORD);

			case ChildRole.TYPE:
				return findChildByType(TYPE);
		}
	}

	@Override
	public int getChildRole(ASTNode child)
	{
		LOG.assertTrue(child.getTreeParent() == this);
		IElementType i = child.getElementType();
		if(i == TYPE)
		{
			return ChildRole.TYPE;
		}
		else if(i == INSTANCEOF_KEYWORD)
		{
			return ChildRole.INSTANCEOF_KEYWORD;
		}
		else
		{
			if(EXPRESSION_BIT_SET.contains(child.getElementType()))
			{
				return ChildRole.OPERAND;
			}
			return ChildRoleBase.NONE;
		}
	}

	@Override
	public void accept(@Nonnull PsiElementVisitor visitor)
	{
		if(visitor instanceof JavaElementVisitor)
		{
			((JavaElementVisitor) visitor).visitInstanceOfExpression(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	@Override
	public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
									   @Nonnull ResolveState state,
									   PsiElement lastParent,
									   @Nonnull PsiElement place)
	{
		if(lastParent != null)
		{
			return true;
		}
		ElementClassHint elementClassHint = processor.getHint(ElementClassHint.KEY);
		if(elementClassHint != null && !elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE))
		{
			return true;
		}
		if(state.get(PatternResolveState.KEY) == PatternResolveState.WHEN_FALSE)
		{
			return true;
		}
		PsiPattern pattern = getPattern();
		if(pattern == null)
		{
			return true;
		}
		return pattern.processDeclarations(processor, state, null, place);
	}

	public String toString()
	{
		return "PsiInstanceofExpression:" + getText();
	}
}

