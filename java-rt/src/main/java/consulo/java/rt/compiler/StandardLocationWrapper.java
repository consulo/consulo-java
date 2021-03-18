package consulo.java.rt.compiler;

import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;

/**
 * @author VISTALL
 * @since 13/03/2021
 */
public class StandardLocationWrapper
{
	public static JavaFileManager.Location MODULE_PATH()
	{
		return StandardLocation.valueOf("MODULE_PATH");
	}
}
