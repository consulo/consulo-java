package consulo.java.codeInsight;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author VISTALL
 * @since 13.10.2015
 */
@State(
		name = "JavaCodeInsightSettings",
		storages = {
				@Storage(
						file = StoragePathMacros.APP_CONFIG + "/editor.codeinsight.xml")
		})
public class JavaCodeInsightSettings implements PersistentStateComponent<JavaCodeInsightSettings>
{
	@NotNull
	public static JavaCodeInsightSettings getInstance()
	{
		return ServiceManager.getService(JavaCodeInsightSettings.class);
	}

	public boolean USE_INSTANCEOF_ON_EQUALS_PARAMETER = false;
	public boolean USE_ACCESSORS_IN_EQUALS_HASHCODE = false;

	@Nullable
	@Override
	public JavaCodeInsightSettings getState()
	{
		return this;
	}

	@Override
	public void loadState(JavaCodeInsightSettings state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
