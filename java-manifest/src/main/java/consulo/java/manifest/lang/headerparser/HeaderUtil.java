package consulo.java.manifest.lang.headerparser;

import consulo.java.manifest.internal.header.ManifestHeaderParserRegistratorImpl;
import consulo.language.psi.PsiElement;
import org.osmorc.manifest.lang.headerparser.HeaderParser;
import org.osmorc.manifest.lang.psi.Header;
import org.osmorc.manifest.lang.psi.HeaderValuePart;

import javax.annotation.Nonnull;

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
    ManifestHeaderParserRegistratorImpl registrator = ManifestHeaderParserRegistratorImpl.get();
    HeaderParser parser = registrator.getParsers().get(headerName);
    if (parser != null) {
      return parser;
    }
    return registrator.getDefaultParser();
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
