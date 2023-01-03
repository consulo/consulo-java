/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.language.projectRoots;

import com.intellij.java.language.util.Bitness;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.application.util.JavaVersion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author nik
 */
@ServiceAPI(ComponentScope.APPLICATION)
public abstract class OwnJdkVersionDetector {
  public static OwnJdkVersionDetector getInstance() {
    return ServiceManager.getService(OwnJdkVersionDetector.class);
  }

  /**
   * @deprecated use {@link #detectJdkVersionInfo(String)} (to be removed in IDEA 2019)
   */
  @Deprecated
  @Nullable
  public String detectJdkVersion(@Nonnull String homePath) {
    JdkVersionInfo info = detectJdkVersionInfo(homePath);
    return info != null ? info.getVersion() : null;
  }

  @Nullable
  public abstract JdkVersionInfo detectJdkVersionInfo(@Nonnull String homePath);

  public static final class JdkVersionInfo {
    public final JavaVersion version;
    public final Bitness bitness;

    public JdkVersionInfo(@Nonnull JavaVersion version, @Nonnull Bitness bitness) {
      this.version = version;
      this.bitness = bitness;
    }

    @Override
    public String toString() {
      return version + " " + bitness;
    }

    /**
     * @deprecated use {@link #version} (to be removed in IDEA 2019)
     */
    @Deprecated
    public String getVersion() {
      return formatVersionString(version);
    }

    /**
     * @deprecated use {@link #bitness} (to be removed in IDEA 2019)
     */
    @Deprecated
    public Bitness getBitness() {
      return bitness;
    }
  }

  public static String formatVersionString(@Nonnull JavaVersion version) {
    return "java version \"" + version + '"';
  }
}