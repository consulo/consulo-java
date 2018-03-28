package consulo.java.manifest.lang.headerparser;

import javax.annotation.Nonnull;
import org.osmorc.manifest.lang.headerparser.HeaderParser;
import org.osmorc.manifest.lang.psi.Header;
import org.osmorc.manifest.lang.psi.HeaderValuePart;
import com.intellij.psi.PsiElement;

/**
 * @author VISTALL
 * @since 14:32/27.04.13
 */
public class HeaderUtil {
	public static HeaderParser getHeaderParser(@Nonnull HeaderValuePart manifestHeaderValue) {
    Header manifestHeader = findHeader(manifestHeaderValue);
    String headerName = manifestHeader != null ? manifestHeader.getName() : null;
    return getHeaderParser(headerName);
  }

  public static HeaderParser getHeaderParser(String headerName) {
    for(HeaderParserEP ep : HeaderParserEP.EP_NAME.getExtensions()) {
      if(ep.key.equals(headerName)) {
        return ep.getParserInstance();
      }
    }

    return getHeaderParser("");
  }

  private static Header findHeader(PsiElement element) {
    if (element == null) {
      return null;
    }
    else if (element instanceof Header) {
      return (Header)element;
    }
    return findHeader(element.getParent());
  }
}
