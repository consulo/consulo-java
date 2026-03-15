package consulo.java.deadCodeNotWorking;

import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import org.jdom.Element;

/**
 * This inspection style not worked - just for compilation
 *
 * @author VISTALL
 * @since 25/03/2023
 */
@Deprecated
public interface OldStyleInspection
{
	default Object createOptionsPanel()
	{
		throw new Error();
	}

	default void readSettings(Element node) throws InvalidDataException
	{
		throw new Error();
	}

	default void writeSettings(Element node) throws WriteExternalException
	{
		throw new Error();
	}
}
