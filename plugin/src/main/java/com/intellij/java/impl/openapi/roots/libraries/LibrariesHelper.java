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
package com.intellij.java.impl.openapi.roots.libraries;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.content.library.Library;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import java.util.List;

/**
 * author: lesya
 */
@ServiceAPI(ComponentScope.APPLICATION)
public abstract class LibrariesHelper {
  public static LibrariesHelper getInstance() {
    return ServiceManager.getService(LibrariesHelper.class);
  }

  public abstract boolean isClassAvailableInLibrary(final Library library, @NonNls final String fqn);

  public abstract boolean isClassAvailable(@NonNls String[] urls, @NonNls String fqn);

  @Nullable
  public abstract VirtualFile findJarByClass(final Library library, @NonNls String fqn);

  @Nullable
  public abstract VirtualFile findRootByClass(List<VirtualFile> roots, String fqn);
}
