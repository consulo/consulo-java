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
package com.intellij.openapi.projectRoots;

import java.io.File;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;

public abstract class JavaSdk extends SdkType implements JavaSdkType {
  public JavaSdk(@NonNls String name) {
    super(name);
  }

  public static JavaSdk getInstance() {
    return EP_NAME.findExtension(JavaSdk.class);
  }

  public final Sdk createJdk(@Nonnull String jdkName, @Nonnull String jreHome) {
    return createJdk(jdkName, jreHome, true);
  }

  /**
   * @deprecated use {@link #isOfVersionOrHigher(Sdk, JavaSdkVersion)} instead
   */
  public abstract int compareTo(@Nonnull String versionString, @Nonnull String versionNumber);

  public abstract Sdk createJdk(@NonNls String jdkName, @Nonnull String home, boolean isJre);

  @Nullable
  public abstract JavaSdkVersion getVersion(@Nonnull Sdk sdk);

  @Nullable
  public abstract JavaSdkVersion getVersion(@Nonnull String versionString);

  public abstract boolean isOfVersionOrHigher(@Nonnull Sdk sdk, @Nonnull JavaSdkVersion version);

  @Deprecated
  public static boolean checkForJdk(File file) {
    return JdkUtil.checkForJdk(file);
  }

  @Deprecated
  public static boolean checkForJre(String file) {
    return JdkUtil.checkForJre(file);
  }

  @Nullable
  public static String getJdkVersion(final String sdkHome) {
    return SdkVersionUtil.detectJdkVersion(sdkHome);
  }
}
