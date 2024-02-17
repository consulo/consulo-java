// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.projectRoots;

import com.intellij.java.language.LanguageLevel;
import consulo.application.util.JavaVersion;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;

/**
 * Represents version of Java SDK. Use {@code JavaSdk#getVersion(Sdk)} method to obtain version of an {@code Sdk}.
 *
 * @author nik
 * @see LanguageLevel
 */
public enum JavaSdkVersion {
  JDK_1_0(LanguageLevel.JDK_1_3),
  JDK_1_1(LanguageLevel.JDK_1_3),
  JDK_1_2(LanguageLevel.JDK_1_3),
  JDK_1_3(LanguageLevel.JDK_1_3),
  JDK_1_4(LanguageLevel.JDK_1_4),
  JDK_1_5(LanguageLevel.JDK_1_5),
  JDK_1_6(LanguageLevel.JDK_1_6),
  JDK_1_7(LanguageLevel.JDK_1_7),
  JDK_1_8(LanguageLevel.JDK_1_8),
  JDK_1_9(LanguageLevel.JDK_1_9),
  JDK_10(LanguageLevel.JDK_10),
  JDK_11(LanguageLevel.JDK_11),
  JDK_12(LanguageLevel.JDK_12),
  JDK_13(LanguageLevel.JDK_13),
  JDK_14(LanguageLevel.JDK_14),
  JDK_15(LanguageLevel.JDK_15),
  JDK_16(LanguageLevel.JDK_16),
  JDK_17(LanguageLevel.JDK_17),
  JDK_18(LanguageLevel.JDK_18),
  JDK_19(LanguageLevel.JDK_19),
  JDK_20(LanguageLevel.JDK_20),
  JDK_21(LanguageLevel.JDK_21),
  JDK_22(LanguageLevel.JDK_22),
  JDK_23(LanguageLevel.JDK_X);

  public static JavaSdkVersion MAX_JDK = JDK_23;

  private final LanguageLevel myMaxLanguageLevel;

  JavaSdkVersion(@Nonnull LanguageLevel maxLanguageLevel) {
    myMaxLanguageLevel = maxLanguageLevel;
  }

  @Nonnull
  public LanguageLevel getMaxLanguageLevel() {
    return myMaxLanguageLevel;
  }

  @Nonnull
  public String getDescription() {
    int feature = ordinal();
    return feature < 5 ? "1." + feature : String.valueOf(feature);
  }

  public boolean isAtLeast(@Nonnull JavaSdkVersion version) {
    return compareTo(version) >= 0;
  }

  @Nonnull
  public static JavaSdkVersion fromLanguageLevel(@Nonnull LanguageLevel languageLevel) throws IllegalArgumentException {
    if (languageLevel == LanguageLevel.JDK_1_3) {
      return JDK_1_3;
    }
    JavaSdkVersion[] values = values();
    if (languageLevel == LanguageLevel.JDK_X) {
      return values[values.length - 1];
    }
    for (JavaSdkVersion version : values) {
      if (version.getMaxLanguageLevel().isAtLeast(languageLevel)) {
        return version;
      }
    }
    throw new IllegalArgumentException("Can't map " + languageLevel + " to any of " + Arrays.toString(values));
  }

  /**
   * See {@link JavaVersion#parse(String)} for supported formats.
   */
  @Nullable
  public static JavaSdkVersion fromVersionString(@Nonnull String versionString) {
    JavaVersion version = JavaVersion.tryParse(versionString);
    return version != null ? fromJavaVersion(version) : null;
  }

  @Nullable
  public static JavaSdkVersion fromJavaVersion(@Nonnull JavaVersion version) {
    JavaSdkVersion[] values = values();
    return version.feature < values.length ? values[version.feature] : null;
  }
}