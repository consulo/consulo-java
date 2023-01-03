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
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.project.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class SourcePositionProvider {
  public static final ExtensionPointName<SourcePositionProvider> EP_NAME =
    ExtensionPointName.create(SourcePositionProvider.class);

  @Nullable
  public static SourcePosition getSourcePosition(@Nonnull NodeDescriptor descriptor,
                                                 @Nonnull Project project,
                                                 @Nonnull DebuggerContextImpl context) {
    return getSourcePosition(descriptor, project, context, false);
  }

  @Nullable
  public static SourcePosition getSourcePosition(@Nonnull NodeDescriptor descriptor,
                                                 @Nonnull Project project,
                                                 @Nonnull DebuggerContextImpl context,
                                                 boolean nearest) {
    for (SourcePositionProvider provider : EP_NAME.getExtensions()) {
      SourcePosition sourcePosition = provider.computeSourcePosition(descriptor, project, context, nearest);
      if (sourcePosition != null) {
        return sourcePosition;
      }
    }
    return null;
  }

  @Nullable
  protected abstract SourcePosition computeSourcePosition(@Nonnull NodeDescriptor descriptor,
                                                          @Nonnull Project project,
                                                          @Nonnull DebuggerContextImpl context,
                                                          boolean nearest);
}
