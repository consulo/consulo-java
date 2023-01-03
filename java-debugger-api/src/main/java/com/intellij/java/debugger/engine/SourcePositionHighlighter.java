/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.debugger.engine;

import com.intellij.java.debugger.SourcePosition;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.document.util.TextRange;
import consulo.project.DumbService;

import javax.annotation.Nullable;

/**
 * During indexing, only extensions that implement {@link DumbAware} are called.
 * See also {@link DumbService}.
 *
 * @author Nikolay.Tropin
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class SourcePositionHighlighter {
  public static final ExtensionPointName<SourcePositionHighlighter> EP_NAME =
    ExtensionPointName.create(SourcePositionHighlighter.class);

  @RequiredReadAction
  public abstract TextRange getHighlightRange(SourcePosition sourcePosition);

  @Nullable
  public static TextRange getHighlightRangeFor(SourcePosition sourcePosition) {
    DumbService dumbService = DumbService.getInstance(sourcePosition.getFile().getProject());
    for (SourcePositionHighlighter provider : dumbService.filterByDumbAwareness(EP_NAME.getExtensionList())) {
      TextRange range = provider.getHighlightRange(sourcePosition);
      if (range != null) {
        return range;
      }
    }
    return null;
  }
}
