// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution.impl;

import com.intellij.java.execution.ShortenCommandLine;
import consulo.execution.configuration.fragment.FragmentStateHolder;
import consulo.java.execution.configurations.fragment.ShortenCommandLineFragmentState;
import consulo.project.Project;

import org.jspecify.annotations.Nullable;

/**
 * A Java run configuration whose command line can be shortened, kept in {@link ShortenCommandLineFragmentState}.
 */
public interface ConfigurationWithCommandLineShortener extends FragmentStateHolder {
  /**
   * @return null if option was not selected explicitly, legacy user-local options to be used
   */
  @Nullable
  default ShortenCommandLine getShortenCommandLine() {
    String mode = getFragmentState(ShortenCommandLineFragmentState.class).getMode();
    if (mode == null) {
      return null;
    }
    for (ShortenCommandLine value : ShortenCommandLine.values()) {
      if (value.name().equals(mode)) {
        return value;
      }
    }
    return null;
  }

  /**
   * Called from UI, when user explicitly selects method to be used to shorten the command line
   */
  default void setShortenCommandLine(@Nullable ShortenCommandLine mode) {
    getFragmentState(ShortenCommandLineFragmentState.class).setMode(mode == null ? null : mode.name());
  }

  Project getProject();
}
