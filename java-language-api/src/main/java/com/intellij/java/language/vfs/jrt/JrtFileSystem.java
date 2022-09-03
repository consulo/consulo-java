// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.vfs.jrt;

import consulo.util.io.URLUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFilePointerCapableFileSystem;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;

import javax.annotation.Nonnull;

public abstract class JrtFileSystem implements ArchiveFileSystem, VirtualFilePointerCapableFileSystem {
  public static final String PROTOCOL = "jrt";
  public static final String PROTOCOL_PREFIX = PROTOCOL + URLUtil.SCHEME_SEPARATOR;
  public static final String SEPARATOR = URLUtil.JAR_SEPARATOR;

  public static boolean isRoot(@Nonnull VirtualFile file) {
    return file.getParent() == null && file.getFileSystem() instanceof JrtFileSystem;
  }

  public static boolean isModuleRoot(@Nonnull VirtualFile file) {
    VirtualFile parent = file.getParent();
    return parent != null && isRoot(parent);
  }
}