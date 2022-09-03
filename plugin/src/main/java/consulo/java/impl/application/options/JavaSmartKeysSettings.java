package consulo.java.impl.application.options;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.jdom.Element;
import consulo.component.persist.PersistentStateComponent;
import consulo.ide.ServiceManager;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.logging.Logger;
import consulo.util.xml.serializer.SkipDefaultValuesSerializationFilters;
import consulo.util.xml.serializer.XmlSerializationException;
import consulo.util.xml.serializer.XmlSerializer;

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
public class JavaSmartKeysSettings implements PersistentStateComponent<Element>
{
	private static final Logger LOGGER = Logger.getInstance(JavaSmartKeysSettings.class);

	@Nonnull
	public static JavaSmartKeysSettings getInstance()
	{
		return ServiceManager.getService(JavaSmartKeysSettings.class);
	}

	public boolean JAVADOC_GENERATE_CLOSING_TAG = true;

	@Override
	public void loadState(final Element state)
	{
		try
		{
			XmlSerializer.deserializeInto(this, state);
		}
		catch(XmlSerializationException e)
		{
			JavaSmartKeysSettings.LOGGER.info(e);
		}
	}

	@Override
	public Element getState()
	{
		Element element = new Element("state");
		writeExternal(element);
		return element;
	}

	public void writeExternal(final Element element)
	{
		try
		{
			XmlSerializer.serializeInto(this, element, new SkipDefaultValuesSerializationFilters());
		}
		catch(XmlSerializationException e)
		{
			JavaSmartKeysSettings.LOGGER.info(e);
		}
	}
}
