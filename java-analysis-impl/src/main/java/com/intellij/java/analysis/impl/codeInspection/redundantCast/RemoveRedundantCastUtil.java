// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.redundantCast;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiParenthesizedExpression;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import com.intellij.java.language.psi.util.PsiPrecedenceUtil;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.logging.Logger;

public class RemoveRedundantCastUtil
{
	private static final Logger LOG = Logger.getInstance(RemoveRedundantCastUtil.class);

	public static PsiExpression removeCast(PsiTypeCastExpression castExpression)
	{
		if(castExpression == null)
		{
			return null;
		}
		PsiElement parent = castExpression.getParent();
		PsiExpression operand = castExpression.getOperand();
		if(operand instanceof PsiParenthesizedExpression)
		{
			final PsiParenthesizedExpression parExpr = (PsiParenthesizedExpression) operand;
			if(!(parent instanceof PsiExpression) ||
					!PsiPrecedenceUtil.areParenthesesNeeded(parExpr.getExpression(), (PsiExpression) parent, true))
			{
				operand = parExpr.getExpression();
			}
		}
		if(operand == null)
		{
			return null;
		}

		PsiExpression toBeReplaced = castExpression;

		while(parent instanceof PsiParenthesizedExpression)
		{
			toBeReplaced = (PsiExpression) parent;
			parent = parent.getParent();
		}

		try
		{
			return (PsiExpression) new CommentTracker().replaceAndRestoreComments(toBeReplaced, operand);
		}
		catch(IncorrectOperationException e)
		{
			LOG.error(e);
		}
		return toBeReplaced;
	}
}
