package com.intellij.java.analysis.impl.codeInspection;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import consulo.util.xml.serializer.XmlSerializerUtil;

import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 19/03/2023
 */
public class AnonymousCanBeLambdaInspectionState implements InspectionToolState<AnonymousCanBeLambdaInspectionState> {
  public boolean reportNotAnnotatedInterfaces = true;

  @Nullable
  @Override
  public AnonymousCanBeLambdaInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(AnonymousCanBeLambdaInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Nullable
  @Override
  public UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
    builder.checkBox(LocalizeValue.localizeTODO("Report when interface is not annotated with @FunctionalInterface"),
                     () -> reportNotAnnotatedInterfaces,
                     b -> reportNotAnnotatedInterfaces = b);
    return builder.buildUnnamed();
  }
}
