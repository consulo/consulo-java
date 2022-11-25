package consulo.java.impl.util;

import com.intellij.java.language.psi.PsiAnnotation;
import consulo.util.lang.StringUtil;

public class AnnotationTextUtil {
  public static void setAnnotationParameter(PsiAnnotation annotation, String param, String value) {
    throw new UnsupportedOperationException();
  }

  public static String quote(String name) {
    return StringUtil.QUOTER.apply(name);
  }

  public static void setAnnotationParameter(PsiAnnotation annotation, String parameterName, String quote, boolean b) {
    throw new UnsupportedOperationException();
  }
}
