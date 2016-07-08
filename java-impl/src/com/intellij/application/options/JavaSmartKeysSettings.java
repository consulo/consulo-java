package com.intellij.application.options;

import consulo.lombok.annotations.ApplicationService;
import consulo.lombok.annotations.Logger;
import org.jdom.Element;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializationException;
import com.intellij.util.xmlb.XmlSerializer;

/**
 * @author VISTALL
 * @since 16.08.14
 */
@ApplicationService
@State(
		name = "JavaSmartKeysSettings",
		storages = {
				@Storage(
						file = StoragePathMacros.APP_CONFIG + "/editor.codeinsight.xml")
		})
@Logger
public class JavaSmartKeysSettings implements PersistentStateComponent<Element>
{
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
			LOGGER.info(e);
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
			LOGGER.info(e);
		}
	}
}
