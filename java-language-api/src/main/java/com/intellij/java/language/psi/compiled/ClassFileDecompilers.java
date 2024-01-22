/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.language.psi.compiled;

import consulo.application.Application;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * An API to extend default IDEA .class file decompiler and handle files compiled from sources other than Java.
 *
 * @since 134.1050
 */
public class ClassFileDecompilers {
  private ClassFileDecompilers() {
  }

  @Nullable
  @Deprecated
  public static ClassFileDecompiler find(@jakarta.annotation.Nonnull VirtualFile file) {
    return find(Application.get(), file);
  }

  @jakarta.annotation.Nullable
  public static ClassFileDecompiler find(@jakarta.annotation.Nonnull Application application, @Nonnull VirtualFile file) {
    for (ClassFileDecompiler decompiler : application.getExtensionList(ClassFileDecompiler.class)) {
      if ((decompiler instanceof ClassFileDecompiler.Light || decompiler instanceof ClassFileDecompiler.Full) && decompiler.accepts(file)) {
        return decompiler;
      }
    }

    return null;
  }
}
