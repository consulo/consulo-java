// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi.util;

import com.intellij.java.language.LanguageLevel;
import consulo.application.Application;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveFileType;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Utilities to support multi-release JAR modules (JEP 238).
 */
public final class JavaMultiReleaseUtil {
  /**
   * Maximal JDK version which does not support multi-release Jars
   */
  public static final LanguageLevel MAX_NON_MULTI_RELEASE_VERSION = LanguageLevel.JDK_1_8;
  /**
   * Minimal JDK version which supports multi-release Jars
   */
  public static final LanguageLevel MIN_MULTI_RELEASE_VERSION = LanguageLevel.JDK_1_9;
  private static final String MAIN = "main";
  private static final Pattern javaVersionPattern = Pattern.compile("java\\d+");

  private JavaMultiReleaseUtil() {
  }

  /**
   * @param mainModule main module candidate (where common code for different releases resides)
   * @param additionalModule additional module candidate (where release-specific code resides)
   * @return true if the supplied modules are indeed main module and additional module
   */
  public static boolean areMainAndAdditionalMultiReleaseModules(@Nullable Module mainModule, @Nullable Module additionalModule) {
    if (mainModule == null || additionalModule == null) return false;
    if (getMainMultiReleaseModule(additionalModule) == mainModule) {
      return true;
    }

    // Fallback: Gradle and JPS
    String mainModuleName = mainModule.getName();
    if (mainModuleName.endsWith("." + MAIN)) {
      String baseModuleName = StringUtil.substringBeforeLast(mainModuleName, MAIN);
      String moduleName = additionalModule.getName();
      return javaVersionPattern.matcher(ObjectUtil.coalesce(StringUtil.substringAfter(moduleName, baseModuleName), moduleName)).matches();
    }
    return false;
  }

  /**
   * @param additionalModule additional module (where release-specific code resides)
   * @return main module (where common code for different releases resides); null if the supplied module is not recognized as
   * an additional module
   */
  public static @Nullable Module getMainMultiReleaseModule(Module additionalModule) {
    for (JavaMultiReleaseModuleSupport support : Application.get().getExtensionList(JavaMultiReleaseModuleSupport.class)) {
      Module result = support.getMainMultiReleaseModule(additionalModule);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /**
   * @param root content root
   * @param relativePath path to the file relative to content root
   * @param level desired language level
   * @return a version of the file, which should be loaded on the specified language level. Returns null if the file is not found.
   */
  public static @Nullable VirtualFile findVersionSpecificFile(VirtualFile root, String relativePath, LanguageLevel level) {
    VirtualFile file = root.findFileByRelativePath(relativePath);
    if (!(root.getFileType() instanceof ArchiveFileType)) return file;
    return findFileImpl(file, level, root, relativePath);
  }

  private static @Nullable VirtualFile findFileImpl(@Nullable VirtualFile defaultFile,
                                                    LanguageLevel level,
                                                    VirtualFile root,
                                                    String relativePath) {
    VirtualFile metaInf = root.findChild("META-INF");
    if (metaInf == null) return defaultFile;
    VirtualFile versions = metaInf.findChild("versions");
    if (versions == null) return defaultFile;
    int feature = level.feature();
    int minFeature = MIN_MULTI_RELEASE_VERSION.feature();
    while (feature >= minFeature) {
      VirtualFile versionRoot = versions.findChild(String.valueOf(feature));
      if (versionRoot != null) {
        VirtualFile target = versionRoot.findFileByRelativePath(relativePath);
        if (target != null) {
          return target;
        }
      }
      feature--;
    }
    return defaultFile;
  }
}
