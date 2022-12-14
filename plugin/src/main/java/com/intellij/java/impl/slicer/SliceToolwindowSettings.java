/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.slicer;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;

@Singleton
@State(
  name = "SliceToolwindowSettings",
  storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)}
)
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class SliceToolwindowSettings implements PersistentStateComponent<SliceToolwindowSettings> {
  private boolean isPreview;
  private boolean isAutoScroll;

  public static SliceToolwindowSettings getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, SliceToolwindowSettings.class);
  }

  public boolean isPreview() {
    return isPreview;
  }

  public void setPreview(boolean preview) {
    isPreview = preview;
  }

  public boolean isAutoScroll() {
    return isAutoScroll;
  }

  public void setAutoScroll(boolean autoScroll) {
    isAutoScroll = autoScroll;
  }

  @Override
  public SliceToolwindowSettings getState() {
    return this;
  }

  @Override
  public void loadState(SliceToolwindowSettings state) {
    isAutoScroll = state.isAutoScroll();
    isPreview = state.isPreview();
  }
}
