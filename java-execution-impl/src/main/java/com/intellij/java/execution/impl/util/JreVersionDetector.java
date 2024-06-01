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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 03.08.2006
 * Time: 14:01:20
 */
package com.intellij.java.execution.impl.util;

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.projectRoots.OwnJdkVersionDetector;
import consulo.content.bundle.Sdk;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.java.language.bundle.JavaSdkTypeUtil;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;

public class JreVersionDetector {
  private String myLastAlternativeJrePath = null; //awful hack
  private boolean myLastIsJre50;


  public boolean isModuleJre50Configured(final ModuleBasedConfiguration configuration) {
    final Module module = configuration.getConfigurationModule().getModule();
    if (module != null && !module.isDisposed()) {
      final Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
      return isJre50(sdk);
    }

    return false;
  }

  public boolean isJre50Configured(final CommonJavaRunConfigurationParameters configuration) {
    if (configuration.isAlternativeJrePathEnabled()) {
      if (configuration.getAlternativeJrePath().equals(myLastAlternativeJrePath)) {
        return myLastIsJre50;
      }
      myLastAlternativeJrePath = configuration.getAlternativeJrePath();
      OwnJdkVersionDetector.JdkVersionInfo jdkInfo = OwnJdkVersionDetector.getInstance().detectJdkVersionInfo(myLastAlternativeJrePath);
      myLastIsJre50 = jdkInfo != null && jdkInfo.version.feature >= 5;
      return myLastIsJre50;
    }
    return false;
  }

  private static boolean isJre50(final Sdk jdk) {
    if (jdk == null) {
      return false;
    }
    return JavaSdkTypeUtil.isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_5);
  }
}