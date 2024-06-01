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

import com.intellij.java.language.internal.DefaultJavaSdkType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkModificator;
import consulo.content.bundle.SdkTable;
import consulo.content.bundle.SdkType;
import consulo.process.cmd.GeneralCommandLine;
import jakarta.annotation.Nonnull;

import java.io.File;

public abstract class JavaSdkType extends SdkType {
  /**
   * Return default impl JavaSdkType implementation
   */
  @Nonnull
  public static JavaSdkType getDefaultJavaSdkType() {
    return EP_NAME.findExtensionOrFail(DefaultJavaSdkType.class);
  }

  public JavaSdkType(String name) {
    super(name);
  }

  public final Sdk createJdk(String jdkName, @Nonnull String home) {
    Sdk jdk = SdkTable.getInstance().createSdk(jdkName, this);
    SdkModificator sdkModificator = jdk.getSdkModificator();

    String path = home.replace(File.separatorChar, '/');
    sdkModificator.setHomePath(path);
    sdkModificator.setVersionString(jdkName); // must be set after home path, otherwise setting home path clears the version string
    sdkModificator.commitChanges();

    setupSdkPaths(jdk);

    return jdk;
  }

  public abstract String getBinPath(Sdk sdk);

  public abstract String getToolsPath(Sdk sdk);

  public abstract void setupCommandLine(@Nonnull GeneralCommandLine generalCommandLine, @Nonnull Sdk sdk);
}
