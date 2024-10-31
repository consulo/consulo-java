package consulo.java.analysis.codeInsight;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.java.analysis.codeInsight.localize.JavaCodeInsightLocalize;
import org.jetbrains.annotations.PropertyKey;
import consulo.component.util.localize.AbstractBundle;

/**
 * @author VISTALL
 * @since 13.10.2015
 */
@Deprecated
@DeprecationInfo("Use JavaCodeInsightLocalize")
@MigratedExtensionsTo(JavaCodeInsightLocalize.class)
public class JavaCodeInsightBundle extends AbstractBundle
{
	private static final JavaCodeInsightBundle ourInstance = new JavaCodeInsightBundle();

	private JavaCodeInsightBundle()
	{
		super("messages.JavaCodeInsightBundle");
	}

	public static String message(@PropertyKey(resourceBundle = "messages.JavaCodeInsightBundle") String key)
	{
		return ourInstance.getMessage(key);
	}

	public static String message(@PropertyKey(resourceBundle = "messages.JavaCodeInsightBundle") String key, Object... params)
	{
		return ourInstance.getMessage(key, params);
	}
}
