package consulo.java.impl.intelliLang.pattern;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import consulo.util.xml.serializer.XmlSerializerUtil;

import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 25/03/2023
 */
public class PatternValidatorState implements InspectionToolState<PatternValidatorState>
{
	public boolean CHECK_NON_CONSTANT_VALUES = true;

	@Nullable
	@Override
	public UnnamedConfigurable createConfigurable()
	{
		ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
		// If checked, the inspection will flag expressions with unknown values and offer to add a substitution (@Subst) annotation
		builder.checkBox(LocalizeValue.localizeTODO("Flag non compile-time constant expressions"),
				() -> CHECK_NON_CONSTANT_VALUES,
				b -> CHECK_NON_CONSTANT_VALUES = b);
		return builder.buildUnnamed();
	}

	@Nullable
	@Override
	public PatternValidatorState getState()
	{
		return this;
	}

	@Override
	public void loadState(PatternValidatorState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
