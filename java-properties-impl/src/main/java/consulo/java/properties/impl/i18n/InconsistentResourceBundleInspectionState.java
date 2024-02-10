package consulo.java.properties.impl.i18n;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 25/03/2023
 */
public class InconsistentResourceBundleInspectionState implements InspectionToolState<InconsistentResourceBundleInspectionState>
{
	public boolean REPORT_MISSING_TRANSLATIONS = true;
	public boolean REPORT_INCONSISTENT_PROPERTIES = true;
	public boolean REPORT_DUPLICATED_PROPERTIES = true;

	@Nullable
	@Override
	public UnnamedConfigurable createConfigurable()
	{
		ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
		builder.checkBox(LocalizeValue.localizeTODO("Report &inconsistent properties"),
				() -> REPORT_INCONSISTENT_PROPERTIES,
				b -> REPORT_INCONSISTENT_PROPERTIES = b);
		builder.checkBox(LocalizeValue.localizeTODO("Report &missing translations"),
				() -> REPORT_MISSING_TRANSLATIONS,
				b -> REPORT_MISSING_TRANSLATIONS = b);
		builder.checkBox(LocalizeValue.localizeTODO("Report properties &overridden with the same value"),
				() -> REPORT_DUPLICATED_PROPERTIES,
				b -> REPORT_DUPLICATED_PROPERTIES = b);
		return null;
	}

	@Nullable
	@Override
	public InconsistentResourceBundleInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(InconsistentResourceBundleInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
