/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.ui;

import com.intellij.java.impl.codeInspection.deadCode.DummyEntryPointsTool;
import consulo.application.AllIcons;
import consulo.ide.impl.idea.codeInspection.ex.GlobalInspectionContextImpl;
import consulo.ide.impl.idea.codeInspection.ui.InspectionNode;
import consulo.language.editor.inspection.scheme.GlobalInspectionToolWrapper;
import consulo.language.editor.inspection.scheme.InspectionToolWrapper;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

/**
 * @author max
 */
public class EntryPointsNode extends InspectionNode {
  public EntryPointsNode(@Nonnull GlobalInspectionContextImpl context) {
    super(createDummyWrapper(context));
  }

  private static InspectionToolWrapper createDummyWrapper(@Nonnull GlobalInspectionContextImpl context) {
    InspectionToolWrapper toolWrapper = new GlobalInspectionToolWrapper(new DummyEntryPointsTool(), new HighlightDisplayKey("Dummy", "dummy"));
    toolWrapper.initialize(context);
    return toolWrapper;
  }

  @Override
  public Image getIcon() {
    return AllIcons.Nodes.EntryPoints;
  }
}
