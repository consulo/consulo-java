/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.language.psi.PsiFile;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.ui.Component;
import consulo.ui.event.UIEvent;
import consulo.util.concurrent.AsyncResult;

import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

@ExtensionAPI(ComponentScope.PROJECT)
public interface AttachSourcesProvider {
  @Nonnull
  Collection<AttachSourcesAction> getActions(List<LibraryOrderEntry> orderEntries, PsiFile psiFile);

  interface AttachSourcesAction {
    String getName();

    String getBusyText();

    AsyncResult<Void> perform(@Nonnull List<LibraryOrderEntry> orderEntriesContainingFile, @Nonnull UIEvent<Component> e);
  }

  /**
   * This marker interface means what this action will be shown only if it is single action.
   */
  interface LightAttachSourcesAction extends AttachSourcesAction {

  }

}
