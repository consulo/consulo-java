package consulo.java.manifest.lang.headerparser;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.TestOnly;
import org.osmorc.manifest.lang.headerparser.HeaderParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author VISTALL
 * @since 13:06/27.04.13
 */
public class HeaderParserEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<HeaderParserEP> EP_NAME = ExtensionPointName.create("consulo.java.manifest.headerParser");

  @Nonnull
  @Attribute("key")
  public String key;

  @Nonnull
  @Attribute("implementationClass")
  public String implementationClass;

  public HeaderParserEP() {

  }

  @TestOnly
  public HeaderParserEP(@Nonnull String key, @Nonnull Class<? extends HeaderParser> clazz) {
    this.key = key;
    this.implementationClass = clazz.getName();
  }

  private HeaderParser myParserInstance;

  public HeaderParser getParserInstance() {
    if(myParserInstance == null) {
      try {
        myParserInstance = instantiate(implementationClass, ApplicationManager.getApplication().getPicoContainer());
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return myParserInstance;
  }
}
