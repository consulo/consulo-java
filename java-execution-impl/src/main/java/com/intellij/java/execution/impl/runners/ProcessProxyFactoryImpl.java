/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.runners;

import com.intellij.java.execution.configurations.JavaCommandLine;
import com.intellij.java.execution.runners.ProcessProxy;
import com.intellij.java.execution.runners.ProcessProxyFactory;
import com.intellij.java.language.impl.projectRoots.ex.JavaSdkUtil;
import com.intellij.java.language.projectRoots.JavaSdkVersion;
import com.intellij.java.language.psi.CommonClassNames;
import consulo.annotation.component.ServiceImpl;
import consulo.container.plugin.PluginManager;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.rt.JavaRtClassNames;
import consulo.java.rt.execution.application.AppMainV2Constants;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.ParametersList;
import consulo.util.lang.StringUtil;
import jakarta.inject.Singleton;

import java.io.File;

@Singleton
@ServiceImpl
public class ProcessProxyFactoryImpl extends ProcessProxyFactory {
  private static final boolean ourMayUseLauncher = !Boolean.valueOf(Platform.current().jvm().getRuntimeProperty("idea.no.launcher"));

  @Override
  public ProcessProxy createCommandLineProxy(JavaCommandLine javaCmdLine) throws ExecutionException {
    OwnJavaParameters javaParameters = javaCmdLine.getJavaParameters();
    String mainClass = javaParameters.getMainClass();

    if (ourMayUseLauncher && mainClass != null) {
      String rtJarPath = JavaSdkUtil.getJavaRtJarPath();
      boolean runtimeJarFile = new File(rtJarPath).isFile();

      if (runtimeJarFile || javaParameters.getModuleName() == null) {
        try {
          ProcessProxyImpl proxy = new ProcessProxyImpl(StringUtil.getShortName(mainClass));
          String port = String.valueOf(proxy.getPortNumber());
          String binPath = new File(PluginManager.getPluginPath(CommonClassNames.class), "breakgen").getPath();

          if (runtimeJarFile && JavaSdkUtil.isJdkAtLeast(javaParameters.getJdk(), JavaSdkVersion.JDK_1_5)) {
            javaParameters.getVMParametersList().add("-javaagent:" + rtJarPath + '=' + port + ':' + binPath);
          }
          else {
            JavaSdkUtil.addRtJar(javaParameters.getClassPath());

            ParametersList vmParametersList = javaParameters.getVMParametersList();
            vmParametersList.defineProperty(AppMainV2Constants.LAUNCHER_PORT_NUMBER, port);
            vmParametersList.defineProperty(AppMainV2Constants.LAUNCHER_BIN_PATH, binPath);

            javaParameters.getProgramParametersList().prepend(mainClass);
            javaParameters.setMainClass(JavaRtClassNames.APP_MAINV2);
          }

          return proxy;
        }
        catch (Exception e) {
          Logger.getInstance(ProcessProxy.class).warn(e);
        }
      }
    }

    return null;
  }

  @Override
  public ProcessProxy getAttachedProxy(ProcessHandler processHandler) {
    return ProcessProxyImpl.KEY.get(processHandler);
  }
}