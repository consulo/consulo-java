package org.osmorc.manifest.lang.headerparser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author VISTALL
 * @since 13:06/27.04.13
 */
public class HeaderParserEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<HeaderParserEP> EP_NAME = ExtensionPointName.create("org.consulo.java.manifest.headerParser");

  @NotNull
  @Attribute("key")
  public String key;

  @NotNull
  @Attribute("implementationClass")
  public String implementationClass;

  public HeaderParserEP() {

  }

  @TestOnly
  public HeaderParserEP(@NotNull String key, @NotNull Class<? extends HeaderParser> clazz) {
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
