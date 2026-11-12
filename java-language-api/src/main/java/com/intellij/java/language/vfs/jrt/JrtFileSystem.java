// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.vfs.jrt;

import consulo.util.io.URLUtil;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileSystem;

/**
 * The modules of the modular runtimes, as archives of their homes: {@code jrt://<home>!/<module>/<path>}, and for a
 * runtime of another platform {@code platform-jrt://<platform id>/<home>!/<module>/<path>}.
 */
public final class JrtFileSystem {
  public static final String PROTOCOL = "jrt";
  public static final String PROTOCOL_PREFIX = PROTOCOL + URLUtil.SCHEME_SEPARATOR;
  public static final String SEPARATOR = URLUtil.JAR_SEPARATOR;

  /**
   * The protocol of the runtimes of other platforms - the archives of other platforms are {@code platform-<protocol>}.
   */
  public static final String PLATFORM_PROTOCOL = StandardFileSystems.PLATFORM_PROTOCOL + "-" + PROTOCOL;
  public static final String PLATFORM_PROTOCOL_PREFIX = PLATFORM_PROTOCOL + URLUtil.SCHEME_SEPARATOR;

  private JrtFileSystem() {
  }

  public static boolean isJrt(VirtualFileSystem fileSystem) {
    String protocol = fileSystem.getProtocol();
    return PROTOCOL.equals(protocol) || PLATFORM_PROTOCOL.equals(protocol);
  }

  public static boolean isRoot(VirtualFile file) {
    return file.getParent() == null && isJrt(file.getFileSystem());
  }

  public static boolean isModuleRoot(VirtualFile file) {
    VirtualFile parent = file.getParent();
    return parent != null && isRoot(parent);
  }
}
