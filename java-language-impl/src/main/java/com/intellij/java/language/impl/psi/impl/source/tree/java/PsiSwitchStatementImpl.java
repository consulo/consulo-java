// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import consulo.language.ast.ASTNode;
import consulo.logging.Logger;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.psi.PsiElementVisitor;
import com.intellij.java.language.psi.PsiSwitchStatement;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import consulo.language.ast.ChildRoleBase;
import consulo.language.ast.IElementType;
import jakarta.annotation.Nonnull;

public class PsiSwitchStatementImpl extends PsiSwitchBlockImpl implements PsiSwitchStatement
{
	private static final Logger LOG = Logger.getInstance(PsiSwitchStatementImpl.class);

	public PsiSwitchStatementImpl()
	{
		super(JavaElementType.SWITCH_STATEMENT);
	}

	@Override
	public ASTNode findChildByRole(int role)
	{
		LOG.assertTrue(ChildRole.isUnique(role));
		switch(role)
		{
			case ChildRole.SWITCH_KEYWORD:
				return findChildByType(JavaTokenType.SWITCH_KEYWORD);
			case ChildRole.LPARENTH:
				return findChildByType(JavaTokenType.LPARENTH);
			case ChildRole.SWITCH_EXPRESSION:
				return findChildByType(ElementType.EXPRESSION_BIT_SET);
			case ChildRole.RPARENTH:
				return findChildByType(JavaTokenType.RPARENTH);
			case ChildRole.SWITCH_BODY:
				return findChildByType(JavaElementType.CODE_BLOCK);
			default:
				return null;
		}
	}

	@Override
	public int getChildRole(@Nonnull ASTNode child)
	{
		LOG.assertTrue(child.getTreeParent() == this);
		IElementType i = child.getElementType();
		if(i == JavaTokenType.SWITCH_KEYWORD)
		{
			return ChildRole.SWITCH_KEYWORD;
		}
		if(i == JavaTokenType.LPARENTH)
		{
			return ChildRole.LPARENTH;
		}
		if(i == JavaTokenType.RPARENTH)
		{
			return ChildRole.RPARENTH;
		}
		if(ElementType.EXPRESSION_BIT_SET.contains(child.getElementType()))
		{
			return ChildRole.SWITCH_EXPRESSION;
		}
		if(child.getElementType() == JavaElementType.CODE_BLOCK)
		{
			return ChildRole.SWITCH_BODY;
		}
		return ChildRoleBase.NONE;
	}

	@Override
	public void accept(@Nonnull PsiElementVisitor visitor)
	{
		if(visitor instanceof JavaElementVisitor)
		{
			((JavaElementVisitor) visitor).visitSwitchStatement(this);
		}
		else
		{
			visitor.visitElement(this);
		}
	}

	@Override
	public String toString()
	{
		return "PsiSwitchStatement";
	}
}