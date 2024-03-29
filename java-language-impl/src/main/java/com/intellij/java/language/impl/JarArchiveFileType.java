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
package com.intellij.java.language.impl;

import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.archive.ArchiveFileType;

import jakarta.annotation.Nonnull;

public class JarArchiveFileType extends ArchiveFileType {
  public static final String PROTOCOL = "jar";
  public static final JarArchiveFileType INSTANCE = new JarArchiveFileType();

  private JarArchiveFileType() {
    super(VirtualFileManager.getInstance());
  }

  @Nonnull
  @Override
  public String getId() {
    return "JAR_ARCHIVE";
  }

  @Nonnull
  @Override
  public String getProtocol() {
    return PROTOCOL;
  }
}