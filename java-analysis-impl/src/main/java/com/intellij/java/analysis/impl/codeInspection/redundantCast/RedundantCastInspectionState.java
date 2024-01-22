package com.intellij.java.analysis.impl.codeInspection.redundantCast;

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
public class RedundantCastInspectionState implements InspectionToolState<RedundantCastInspectionState> {
  public boolean IGNORE_SUSPICIOUS_METHOD_CALLS;

  @Nullable
  @Override
  public UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
    builder.checkBox(LocalizeValue.localizeTODO("Ignore casts in suspicious collections method calls"),
                     () -> IGNORE_SUSPICIOUS_METHOD_CALLS,
                     b -> IGNORE_SUSPICIOUS_METHOD_CALLS = b);
    return builder.buildUnnamed();
  }

  @Nullable
  @Override
  public RedundantCastInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(RedundantCastInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
