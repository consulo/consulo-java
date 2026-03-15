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
package com.intellij.java.language.impl.projectRoots.ex;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.projectRoots.JavaSdkVersionUtil;
import com.intellij.java.language.projectRoots.JavaSdkType;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import consulo.annotation.DeprecationInfo;
import consulo.container.plugin.PluginManager;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTypeId;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.virtualFileSystem.util.PathsList;
import org.jspecify.annotations.Nullable;
import org.jetbrains.annotations.Contract;


import java.io.File;

public class JavaSdkUtil {
  public static void addRtJar(PathsList pathsList) {
    pathsList.addFirst(getJavaRtJarPath());
  }

  @Deprecated
  @DeprecationInfo("Use #getJavaRtJarPath()")
  public static String getIdeaRtJarPath() {
    return getJavaRtJarPath();
  }

  public static String getJavaRtJarPath() {
    File pluginPath = PluginManager.getPluginPath(JavaSdkUtil.class);
    File jarFile = new File(pluginPath, "java-rt-shaded.jar");
    return jarFile.getPath();
  }

  public static String getJavaRtJarNotShadedPath() {
    File pluginPath = PluginManager.getPluginPath(JavaSdkUtil.class);
    File jarFile = new File(pluginPath, "java-rt.jar");
    return jarFile.getPath();
  }

  public static boolean isLanguageLevelAcceptable(Project project, Module module, LanguageLevel level) {
    return isJdkSupportsLevel(getRelevantJdk(project, module), level);
  }

  private static boolean isJdkSupportsLevel(@Nullable final Sdk jdk, LanguageLevel level) {
    if (jdk == null) {
      return true;
    }
    JavaSdkVersion version = JavaSdkVersionUtil.getJavaSdkVersion(jdk);
    return version != null && version.getMaxLanguageLevel().isAtLeast(level);
  }

  @Nullable
  private static Sdk getRelevantJdk(Project project, Module module) {
    Sdk moduleJdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
    return moduleJdk == null ? null : moduleJdk;
  }

  @Contract("null, _ -> false")
  public static boolean isJdkAtLeast(@Nullable Sdk jdk, JavaSdkVersion expected) {
    if (jdk != null) {
      SdkTypeId type = jdk.getSdkType();
      if (type instanceof JavaSdkType) {
        JavaSdkVersion actual = JavaSdkTypeUtil.getVersion(jdk);
        if (actual != null) {
          return actual.isAtLeast(expected);
        }
      }
    }

    return false;
  }
}
