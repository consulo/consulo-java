package consulo.java.codeInsight;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;

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
@Singleton
@State(
		name = "JavaCodeInsightSettings",
		storages = {
				@Storage(
						file = StoragePathMacros.APP_CONFIG + "/editor.codeinsight.xml")
		})
public class JavaCodeInsightSettings implements PersistentStateComponent<JavaCodeInsightSettings>
{
	@Nonnull
	public static JavaCodeInsightSettings getInstance()
	{
		return ServiceManager.getService(JavaCodeInsightSettings.class);
	}

	public boolean USE_INSTANCEOF_ON_EQUALS_PARAMETER = false;
	public boolean USE_ACCESSORS_IN_EQUALS_HASHCODE = false;
	public boolean SHOW_SOURCE_INFERRED_ANNOTATIONS = true;

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
