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
package com.intellij.java.language.projectRoots;

import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkType;
import consulo.process.cmd.GeneralCommandLine;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class JavaSdk extends SdkType implements JavaSdkType {
  public JavaSdk(String name) {
    super(name);
  }

  public static JavaSdk getInstance() {
    return EP_NAME.findExtensionOrFail(JavaSdk.class);
  }

  public final Sdk createJdk(@Nonnull String jdkName, @Nonnull String jreHome) {
    return createJdk(jdkName, jreHome, true);
  }

  /**
   * @deprecated use {@link #isOfVersionOrHigher(Sdk, JavaSdkVersion)} instead
   */
  public abstract int compareTo(@Nonnull String versionString, @Nonnull String versionNumber);

  public abstract Sdk createJdk(@NonNls String jdkName, @jakarta.annotation.Nonnull String home, boolean isJre);

  @Nullable
  public abstract JavaSdkVersion getVersion(@Nonnull Sdk sdk);

  @jakarta.annotation.Nullable
  public abstract JavaSdkVersion getVersion(@jakarta.annotation.Nonnull String versionString);

  public abstract boolean isOfVersionOrHigher(@Nonnull Sdk sdk, @jakarta.annotation.Nonnull JavaSdkVersion version);

  @NonNls
  public abstract String getBinPath(Sdk sdk);

  @NonNls
  public abstract String getToolsPath(Sdk sdk);

  public abstract void setupCommandLine(@jakarta.annotation.Nonnull GeneralCommandLine generalCommandLine, @Nonnull Sdk sdk);
}
