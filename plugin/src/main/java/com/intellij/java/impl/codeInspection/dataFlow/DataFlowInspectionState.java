package com.intellij.java.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.DataFlowInspectionStateBase;
import com.intellij.java.impl.codeInsight.NullableNotNullDialog;
import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import consulo.util.xml.serializer.XmlSerializerUtil;

import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 25/03/2023
 */
public class DataFlowInspectionState extends DataFlowInspectionStateBase implements InspectionToolState<DataFlowInspectionState>
{
	@Nullable
	@Override
	public UnnamedConfigurable createConfigurable()
	{
		ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
		builder.checkBox(JavaInspectionsLocalize.inspectionDataFlowNullableQuickfixOption(),
				() -> SUGGEST_NULLABLE_ANNOTATIONS,
				b -> SUGGEST_NULLABLE_ANNOTATIONS = b);

		builder.checkBox(JavaInspectionsLocalize.inspectionDataFlowTrueAssertsOption(),
				() -> DONT_REPORT_TRUE_ASSERT_STATEMENTS,
				b -> DONT_REPORT_TRUE_ASSERT_STATEMENTS = b);

		builder.checkBox(LocalizeValue.localizeTODO("Ignore assert statements"),
				() -> IGNORE_ASSERT_STATEMENTS,
				b -> IGNORE_ASSERT_STATEMENTS = b);

		builder.checkBox(LocalizeValue.localizeTODO("Warn when reading a value guaranteed to be constant"),
				() -> REPORT_CONSTANT_REFERENCE_VALUES,
				b -> REPORT_CONSTANT_REFERENCE_VALUES = b);

		builder.checkBox(LocalizeValue.localizeTODO("Treat non-annotated members and parameters as @Nullable"),
				() -> TREAT_UNKNOWN_MEMBERS_AS_NULLABLE,
				b -> TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = b);

		builder.checkBox(LocalizeValue.localizeTODO("Report not-null required parameter with null-literal argument usages"),
				() -> REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER,
				b -> REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER = b);

		builder.checkBox(LocalizeValue.localizeTODO("Report nullable methods that always return a non-null value"),
				() -> REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL,
				b -> REPORT_NULLABLE_METHODS_RETURNING_NOT_NULL = b);

		builder.checkBox(LocalizeValue.localizeTODO("Report problems that happen only on some code paths"),
				() -> REPORT_UNSOUND_WARNINGS,
				b -> REPORT_UNSOUND_WARNINGS = b);

		builder.component(() -> NullableNotNullDialog.createConfigureAnnotationsButton());
		return builder.buildUnnamed();
	}

	@Nullable
	@Override
	public DataFlowInspectionState getState()
	{
		return this;
	}

	@Override
	public void loadState(DataFlowInspectionState state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}
}
