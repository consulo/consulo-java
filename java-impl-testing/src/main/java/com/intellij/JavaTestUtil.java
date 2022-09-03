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
package com.intellij;

import consulo.module.Module;
import consulo.language.util.ModuleUtilCore;
import com.intellij.java.language.projectRoots.JavaSdk;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTable;
import consulo.util.lang.StringUtil;
import consulo.container.boot.ContainerPathManager;
import consulo.java.language.module.extension.JavaModuleExtension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author yole
 * @author Konstantin Bulenkov
 */
public class JavaTestUtil {

  public static String getJavaTestDataPath() {
    return "";
  }

  public static String getRelativeJavaTestDataPath() {
    final String absolute = getJavaTestDataPath();
    return StringUtil.trimStart(absolute, ContainerPathManager.get().getHomePath());
  }

  public static void setupTestJDK() {

  }

  public static Sdk getTestJdk() {
    SdkTable sdkTable = SdkTable.getInstance();
    for (Sdk sdk : sdkTable.getAllSdks()) {
      if (sdk.isPredefined() && sdk.getSdkType() instanceof JavaSdk) {
        return sdk;
      }
    }
    return null;
  }

  @Nullable
  public static Sdk getSdk(@Nonnull Module module) {
    return ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
  }
}
