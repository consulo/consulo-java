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
package com.intellij.java.language.impl;

import consulo.java.language.localize.JavaLanguageLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;

public class JavaClassFileType implements FileType {

  public static JavaClassFileType INSTANCE = new JavaClassFileType();

  private JavaClassFileType() {
  }

  @Override
  @Nonnull
  public String getId() {
    return "CLASS";
  }

  @Override
  @Nonnull
  public LocalizeValue getDescription() {
    return JavaLanguageLocalize.filetypeDescriptionClass();
  }

  @Override
  @Nonnull
  public String getDefaultExtension() {
    return "class";
  }

  @Override
  public Image getIcon() {
    return PlatformIconGroup.filetypesBinary();
  }

  @Override
  public boolean isBinary() {
    return true;
  }
}
