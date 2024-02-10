package com.intellij.java.analysis.impl.codeInspection.deprecation;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import consulo.util.xml.serializer.XmlSerializerUtil;

import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 20/03/2023
 */
public class DeprecationInspectionState implements InspectionToolState<DeprecationInspectionState> {
  public boolean IGNORE_INSIDE_DEPRECATED = false;
  public boolean IGNORE_IMPORT_STATEMENTS = true;
  public boolean IGNORE_ABSTRACT_DEPRECATED_OVERRIDES = true;
  public boolean IGNORE_METHODS_OF_DEPRECATED = true;

  @Nullable
  @Override
  public UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
    builder.checkBox(LocalizeValue.localizeTODO("Ignore inside deprecated members"),
                     () -> IGNORE_INSIDE_DEPRECATED,
                     b -> IGNORE_INSIDE_DEPRECATED = b);
    builder.checkBox(LocalizeValue.localizeTODO("Ignore inside non-static imports"),
                     () -> IGNORE_IMPORT_STATEMENTS,
                     b -> IGNORE_IMPORT_STATEMENTS = b);
    builder.checkBox(LocalizeValue.localizeTODO("Ignore overrides of deprecated abstract methods from non-deprecated supers"),
                     () -> IGNORE_ABSTRACT_DEPRECATED_OVERRIDES,
                     b -> IGNORE_ABSTRACT_DEPRECATED_OVERRIDES = b);
    builder.checkBox(LocalizeValue.localizeTODO("Ignore members of deprecated classes"),
                     () -> IGNORE_METHODS_OF_DEPRECATED,
                     b -> IGNORE_METHODS_OF_DEPRECATED = b);
    return builder.buildUnnamed();
  }

  @Nullable
  @Override
  public DeprecationInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(DeprecationInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
