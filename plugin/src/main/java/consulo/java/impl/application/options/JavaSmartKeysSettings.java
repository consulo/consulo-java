package consulo.java.impl.application.options;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.logging.Logger;
import consulo.util.xml.serializer.SkipDefaultValuesSerializationFilters;
import consulo.util.xml.serializer.XmlSerializationException;
import consulo.util.xml.serializer.XmlSerializer;
import jakarta.inject.Singleton;
import org.jdom.Element;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 16.08.14
 */
@Singleton
@State(
  name = "JavaSmartKeysSettings",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/editor.codeinsight.xml")
  })
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class JavaSmartKeysSettings implements PersistentStateComponent<Element> {
  private static final Logger LOGGER = Logger.getInstance(JavaSmartKeysSettings.class);

  @Nonnull
  public static JavaSmartKeysSettings getInstance() {
    return ServiceManager.getService(JavaSmartKeysSettings.class);
  }

  public boolean JAVADOC_GENERATE_CLOSING_TAG = true;

  public void setJavadocGenerateClosingTag(boolean value) {
    JAVADOC_GENERATE_CLOSING_TAG = value;
  }

  public boolean isJavadocGenerateClosingTag() {
    return JAVADOC_GENERATE_CLOSING_TAG;
  }

  @Override
  public void loadState(final Element state) {
    try {
      XmlSerializer.deserializeInto(this, state);
    }
    catch (XmlSerializationException e) {
      LOGGER.info(e);
    }
  }

  @Override
  public Element getState() {
    Element element = new Element("state");
    writeExternal(element);
    return element;
  }

  public void writeExternal(final Element element) {
    try {
      XmlSerializer.serializeInto(this, element, new SkipDefaultValuesSerializationFilters());
    }
    catch (XmlSerializationException e) {
      LOGGER.info(e);
    }
  }
}
