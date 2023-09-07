package com.intellij.java.analysis.impl.codeInspection.nullable;

import consulo.application.Application;
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
 * @since 07/09/2023
 */
public class NullableStuffInspectionState implements InspectionToolState<NullableStuffInspectionState> {
  @Deprecated
  @SuppressWarnings({"WeakerAccess"})
  public boolean REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"})
  public boolean REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"})
  public boolean REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = true;
  @Deprecated
  @SuppressWarnings({"WeakerAccess"})
  public boolean REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"})
  public boolean REPORT_NOT_ANNOTATED_GETTER = true;
  @SuppressWarnings({"WeakerAccess"})
  public boolean IGNORE_EXTERNAL_SUPER_NOTNULL;
  @SuppressWarnings({"WeakerAccess"})
  public boolean REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED;
  @Deprecated
  @SuppressWarnings({"WeakerAccess"})
  public boolean REPORT_NOT_ANNOTATED_SETTER_PARAMETER = true;
  @Deprecated
  @SuppressWarnings({"WeakerAccess"})
  public boolean REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true; // remains for test
  @SuppressWarnings({"WeakerAccess"})
  public boolean REPORT_NULLS_PASSED_TO_NON_ANNOTATED_METHOD = true;
  public boolean REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER = true;

  @Nullable
  @Override
  public UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
    builder.checkBox(JavaInspectionsLocalize.inspectionNullableProblemsMethodOverridesNotnullOption(),
                     () -> REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE,
                     b -> REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = b);
    builder.checkBox(JavaInspectionsLocalize.inspectionNullableProblemsMethodOverridesOption(),
                     () -> REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL,
                     b -> REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = b);
    builder.checkBox(LocalizeValue.localizeTODO("&Ignore external @NotNull"),
                     () -> IGNORE_EXTERNAL_SUPER_NOTNULL,
                     b -> IGNORE_EXTERNAL_SUPER_NOTNULL = b);
    builder.checkBox(LocalizeValue.localizeTODO("Report @NotNull &parameters overriding non-annotated"),
                     () -> REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED,
                     b -> REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = b);
    builder.checkBox(JavaInspectionsLocalize.inspectionNullableProblemsNotAnnotatedGettersForAnnotatedFields(),
                     () -> REPORT_NOT_ANNOTATED_GETTER,
                     b -> REPORT_NOT_ANNOTATED_GETTER = b);
    builder.checkBox(LocalizeValue.localizeTODO("Report @NotNull parameters with null-literal argument usages"),
                     () -> REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER,
                     b -> REPORT_NULLS_PASSED_TO_NOT_NULL_PARAMETER = b);
    builder.component(() -> Application.get().getInstance(NullableNotNullDialogProxy.class).createConfigureAnnotationsButton());
    return builder.buildUnnamed();
  }

  @Nullable
  @Override
  public NullableStuffInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(NullableStuffInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
