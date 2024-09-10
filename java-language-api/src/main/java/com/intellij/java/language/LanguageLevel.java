// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language;

import consulo.application.util.JavaVersion;
import consulo.component.util.pointer.Named;
import consulo.component.util.pointer.NamedPointer;
import consulo.java.language.localize.JavaLanguageLocalize;
import consulo.java.language.psi.JavaLanguageVersion;
import consulo.localize.LocalizeValue;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a language level (i.e. features available) of a Java code.
 * <p>
 * Unsupported language levels are marked as {@link Deprecated} to draw attention. They should not be normally used,
 * except probably in rare tests and inside {@link JavaFeature}.
 *
 * @see JavaSdkVersion
 * @see JavaFeature
 */
public enum LanguageLevel implements Named, NamedPointer<LanguageLevel>  {
  JDK_1_3(JavaLanguageLocalize.jdk1_3LanguageLevelDescription(), 3),
  JDK_1_4(JavaLanguageLocalize.jdk1_4LanguageLevelDescription(), 4),
  JDK_1_5(JavaLanguageLocalize.jdk1_5LanguageLevelDescription(), 5),
  JDK_1_6(JavaLanguageLocalize.jdk1_6LanguageLevelDescription(), 6),
  JDK_1_7(JavaLanguageLocalize.jdk1_7LanguageLevelDescription(), 7),
  JDK_1_8(JavaLanguageLocalize.jdk1_8LanguageLevelDescription(), 8),
  JDK_1_9(JavaLanguageLocalize.jdk1_9LanguageLevelDescription(), 9),
  JDK_10(JavaLanguageLocalize.jdk10LanguageLevelDescription(), 10),
  JDK_11(JavaLanguageLocalize.jdk11LanguageLevelDescription(), 11),
  JDK_12(JavaLanguageLocalize.jdk12LanguageLevelDescription(), 12),
  JDK_13(JavaLanguageLocalize.jdk13LanguageLevelDescription(), 13),
  JDK_14(JavaLanguageLocalize.jdk14LanguageLevelDescription(), 14),
  JDK_15(JavaLanguageLocalize.jdk15LanguageLevelDescription(), 15),
  JDK_16(JavaLanguageLocalize.jdk16LanguageLevelDescription(), 16),
  JDK_17(JavaLanguageLocalize.jdk17LanguageLevelDescription(), 17),
  @Deprecated
  JDK_17_PREVIEW(17),
  JDK_18(JavaLanguageLocalize.jdk18LanguageLevelDescription(), 18),
  @Deprecated
  JDK_18_PREVIEW(18),
  JDK_19(JavaLanguageLocalize.jdk19LanguageLevelDescription(), 19),
  @Deprecated
  JDK_19_PREVIEW(19),
  JDK_20(JavaLanguageLocalize.jdk20LanguageLevelDescription(), 20),
  @Deprecated
  JDK_20_PREVIEW(20),
  JDK_21(JavaLanguageLocalize.jdk21LanguageLevelDescription(), 21),
  JDK_21_PREVIEW(JavaLanguageLocalize.jdk21PreviewLanguageLevelDescription(), 21),
  JDK_22(JavaLanguageLocalize.jdk22LanguageLevelDescription(), 22),
  JDK_22_PREVIEW(JavaLanguageLocalize.jdk22PreviewLanguageLevelDescription(), 22),
  JDK_X(JavaLanguageLocalize.jdkXLanguageLevelDescription(), 23),

  ;
  private static final Map<Integer, LanguageLevel> ourStandardVersions =
    Stream.of(values()).filter(ver -> !ver.isPreview())
          .collect(Collectors.toMap(ver -> ver.myVersion.feature, Function.identity()));

  public static final LanguageLevel HIGHEST = JDK_21;
  public static final Key<LanguageLevel> KEY = Key.create("LANGUAGE_LEVEL");

  /**
   * Should point to the latest released JDK.
   */

  private final LocalizeValue myPresentableText;
  private final JavaVersion myVersion;
  private final boolean myPreview;
  private final boolean myUnsupported;

  private final JavaLanguageVersion myLangVersion;

  LanguageLevel(LocalizeValue presentableTextSupplier, int major) {
    this(presentableTextSupplier, major, false);
  }

  LanguageLevel(int major) {
    this(JavaLanguageLocalize.jdkUnsupportedPreviewLanguageLevelDescription(major), major, true);
  }

  LanguageLevel(LocalizeValue presentableTextSupplier, int major, boolean unsupported) {
    myPresentableText = presentableTextSupplier;
    myVersion = JavaVersion.compose(major);
    myUnsupported = unsupported;
    myPreview = name().endsWith("_PREVIEW") || name().endsWith("_X");
    if (myUnsupported && !myPreview) {
      throw new IllegalArgumentException("Only preview versions could be unsupported: " + name());
    }
    myLangVersion = new JavaLanguageVersion(name(), name(), this);
  }

  public int getMajor() {
    return myVersion.feature;
  }

  /**
   * @return true if this language level is not supported anymore. It's still possible to invoke compiler or launch the program
   * using this language level. However, it's not guaranteed that the code insight features will work correctly.
   */
  public boolean isUnsupported() {
    return myUnsupported;
  }

  /**
   * String representation of the level, suitable to pass as a value of compiler's "-source" and "-target" options
   */
  @Nonnull
  public String getCompilerComplianceDefaultOption() {
    int major = getMajor();
    return major <= 8 ? "1." + major : String.valueOf(major);
  }

  public boolean isPreview() {
    return myPreview;
  }

  @Nonnull
  public JavaLanguageVersion toLangVersion() {
    return myLangVersion;
  }

  @Nonnull
  public LocalizeValue getDescription() {
    return myPresentableText;
  }

  /**
   * @return corresponding preview level, or {@code null} if level has no paired preview level
   */
  @Nullable
  public LanguageLevel getPreviewLevel() {
    if (myPreview) return this;
    try {
      return valueOf(name() + "_PREVIEW");
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }

  public boolean isAtLeast(final LanguageLevel level) {
    return compareTo(level) >= 0;
  }

  /**
   * @param level level to compare to
   * @return true if this language level is strictly less than the level we are comparing to.
   * A preview level for Java version X is assumed to be between non-preview version X and non-preview version X+1
   */
  public boolean isLessThan(@Nonnull LanguageLevel level) {
    return compareTo(level) < 0;
  }

  @Override
  public LanguageLevel get() {
    return this;
  }

  @Nonnull
  @Override
  public String getName() {
    return name();
  }

  /**
   * @return the {@link JavaVersion} object that corresponds to this language level
   */
  @Nonnull
  public JavaVersion toJavaVersion() {
    return myVersion;
  }

  /**
   * @return the language level feature number (like 8 for {@link #JDK_1_8}).
   */
  public int feature() {
    return myVersion.feature;
  }

  @Nonnull
  public static Set<String> getAllCompilerOptions() {
    Set<String> options = new LinkedHashSet<>();
    for (LanguageLevel level : values()) {
      options.add(level.getCompilerComplianceDefaultOption());
    }
    return options;
  }

  @Nullable
  public static LanguageLevel parse(final String compilerComplianceOption) {
    if (StringUtil.isEmpty(compilerComplianceOption)) {
      return null;
    }
    return ContainerUtil.find(values(), level -> Objects.equals(level.getCompilerComplianceDefaultOption(), compilerComplianceOption));
  }
}
