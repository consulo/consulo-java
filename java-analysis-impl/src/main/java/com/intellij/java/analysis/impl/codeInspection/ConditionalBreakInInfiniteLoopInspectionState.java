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
package com.intellij.java.analysis.impl.codeInspection;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.ConfigurableBuilderState;
import consulo.configurable.UnnamedConfigurable;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;
import org.jspecify.annotations.Nullable;

/**
 * The options of {@link ConditionalBreakInInfiniteLoopInspection} (the option pane of JetBrains' inspection).
 */
public class ConditionalBreakInInfiniteLoopInspectionState implements InspectionToolState<ConditionalBreakInInfiniteLoopInspectionState> {
  public boolean noConversionToDoWhile = false;
  public boolean allowConditionFusion = false;
  public boolean suggestConversionWhenIfIsASingleStmtInLoop = false;

  @Override
  public ConditionalBreakInInfiniteLoopInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(ConditionalBreakInInfiniteLoopInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public @Nullable UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder<ConfigurableBuilderState> builder = ConfigurableBuilder.newBuilder();
    builder.checkBox(JavaInspectionsLocalize.inspectionConditionalBreakInInfiniteLoopNoConversionWithDoWhile(),
                     () -> noConversionToDoWhile, b -> noConversionToDoWhile = b);
    builder.checkBox(JavaInspectionsLocalize.inspectionConditionalBreakInInfiniteLoopAllowConditionFusion(),
                     () -> allowConditionFusion, b -> allowConditionFusion = b);
    builder.checkBox(JavaInspectionsLocalize.inspectionConditionalBreakInInfiniteLoopSuggestConversionWhenIfIsSingleStmtInLoop(),
                     () -> suggestConversionWhenIfIsASingleStmtInLoop, b -> suggestConversionWhenIfIsASingleStmtInLoop = b);
    return builder.buildUnnamed();
  }
}
