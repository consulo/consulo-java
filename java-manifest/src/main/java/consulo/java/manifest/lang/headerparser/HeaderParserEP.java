package consulo.java.manifest.lang.headerparser;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.TestOnly;
import org.osmorc.manifest.lang.headerparser.HeaderParser;
import consulo.application.Application;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import consulo.component.extension.ExtensionPointName;
import consulo.util.xml.serializer.annotation.Attribute;

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
        myParserInstance = instantiate(implementationClass, Application.get().getInjectingContainer());
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return myParserInstance;
  }
}
