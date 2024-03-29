/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.language.impl.codeInsight;

import com.intellij.java.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;

public class ExpressionUtil
{
	/**
	 * @return true if refExpression has no qualifier or has this qualifier corresponding to the inner most containing class
	 */
	public static boolean isEffectivelyUnqualified(PsiReferenceExpression refExpression)
	{
		PsiExpression qualifier = PsiUtil.deparenthesizeExpression(refExpression.getQualifierExpression());
		if(qualifier == null)
		{
			return true;
		}
		if(qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression)
		{
			final PsiJavaCodeReferenceElement thisQualifier = ((PsiQualifiedExpression) qualifier).getQualifier();
			if(thisQualifier == null)
				return true;
			final PsiClass innerMostClass = PsiTreeUtil.getParentOfType(refExpression, PsiClass.class);
			return innerMostClass == thisQualifier.resolve();
		}
		return false;
	}
}
