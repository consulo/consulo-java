package consulo.java.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;

public class AnnotationTextUtil
{
	public static void setAnnotationParameter(PsiAnnotation annotation, String param, String value)
	{
		throw new UnsupportedOperationException();
	}

	public static String quote(String name)
	{
		return StringUtil.QUOTER.fun(name);
	}

	public static void setAnnotationParameter(PsiAnnotation annotation, String parameterName, String quote, boolean b)
	{
		throw new UnsupportedOperationException();
	}
}
