package com.intellij.javaee.util;

import javax.annotation.Nonnull;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;

/**
 * @author VISTALL
 * @since 14-Jan-17
 */
public class AnnotationTextUtil
{
	@Nonnull
	public static String quote(String name)
	{
		return StringUtil.QUOTER.fun(name);
	}

	public static void setAnnotationParameter(PsiAnnotation annotation, String param, String value)
	{
		throw new UnsupportedOperationException();
	}

	public static void setAnnotationParameter(PsiAnnotation annotation, String parameterName, String quote, boolean b)
	{
		throw new UnsupportedOperationException();
	}
}
