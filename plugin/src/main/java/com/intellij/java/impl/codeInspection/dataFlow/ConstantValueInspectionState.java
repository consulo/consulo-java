/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.impl.codeInspection.dataFlow;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;
import org.jspecify.annotations.Nullable;

/**
 * The options of {@link ConstantValueInspection} (the option pane of JetBrains' inspection).
 */
public class ConstantValueInspectionState implements InspectionToolState<ConstantValueInspectionState> {
  public boolean DONT_REPORT_TRUE_ASSERT_STATEMENTS;
  public boolean IGNORE_ASSERT_STATEMENTS;
  public boolean REPORT_CONSTANT_REFERENCE_VALUES = true;

  @Override
  public ConstantValueInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(ConstantValueInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public @Nullable UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
    builder.checkBox(JavaInspectionsLocalize.inspectionDataFlowTrueAssertsOption(),
                     () -> DONT_REPORT_TRUE_ASSERT_STATEMENTS, b -> DONT_REPORT_TRUE_ASSERT_STATEMENTS = b);
    builder.checkBox(JavaInspectionsLocalize.inspectionDataFlowIgnoreAssertStatements(),
                     () -> IGNORE_ASSERT_STATEMENTS, b -> IGNORE_ASSERT_STATEMENTS = b);
    builder.checkBox(JavaInspectionsLocalize.inspectionDataFlowReportConstantReferenceValues(),
                     () -> REPORT_CONSTANT_REFERENCE_VALUES, b -> REPORT_CONSTANT_REFERENCE_VALUES = b);
    return builder.buildUnnamed();
  }
}
