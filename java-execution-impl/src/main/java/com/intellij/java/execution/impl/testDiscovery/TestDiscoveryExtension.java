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
package com.intellij.java.execution.impl.testDiscovery;

import com.intellij.java.execution.impl.JavaTestConfigurationBase;
import com.intellij.java.execution.impl.RunConfigurationExtension;
import com.intellij.java.execution.impl.testframework.JavaTestLocator;
import com.intellij.rt.coverage.data.ProjectData;
import consulo.annotation.component.ExtensionImpl;
import consulo.component.messagebus.MessageBusConnection;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.RunnerSettings;
import consulo.execution.test.sm.runner.SMTRunnerEventsAdapter;
import consulo.execution.test.sm.runner.SMTRunnerEventsListener;
import consulo.execution.test.sm.runner.SMTestProxy;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import consulo.project.util.ProjectUtil;
import consulo.ui.ex.awt.util.Alarm;
import consulo.util.collection.ArrayUtil;
import consulo.util.io.ClassPathUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import org.jdom.Element;

import jakarta.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class TestDiscoveryExtension extends RunConfigurationExtension {
  public static final boolean TESTDISCOVERY_ENABLED = Boolean.valueOf(Platform.current().jvm().getRuntimeProperty("testDiscovery.enabled"));

  private static final Logger LOG = Logger.getInstance(TestDiscoveryExtension.class);

  @Nonnull
  @Override
  public String getSerializationId() {
    return "testDiscovery";
  }

  @Override
  protected void attachToProcess(@Nonnull final RunConfigurationBase configuration, @Nonnull final ProcessHandler handler, @Nullable RunnerSettings runnerSettings) {
    if (runnerSettings == null && isApplicableFor(configuration)) {
      final String frameworkPrefix = ((JavaTestConfigurationBase) configuration).getFrameworkPrefix();
      final String moduleName = ((JavaTestConfigurationBase) configuration).getConfigurationModule().getModuleName();

      Disposable disposable = Disposable.newDisposable();
      final Alarm processTracesAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable);
      final MessageBusConnection connection = configuration.getProject().getMessageBus().connect();
      connection.subscribe(SMTRunnerEventsListener.class, new SMTRunnerEventsAdapter() {
        private List<String> myCompletedMethodNames = new ArrayList<>();

        @Override
        public void onTestFinished(@Nonnull SMTestProxy test) {
          final SMTestProxy.SMRootTestProxy root = test.getRoot();
          if ((root == null || root.getHandler() == handler)) {
            final String fullTestName = test.getLocationUrl();
            if (fullTestName != null && fullTestName.startsWith(JavaTestLocator.TEST_PROTOCOL)) {
              myCompletedMethodNames.add(frameworkPrefix + fullTestName.substring(JavaTestLocator.TEST_PROTOCOL.length() + 3));
              if (myCompletedMethodNames.size() > 50) {
                final String[] fullTestNames = ArrayUtil.toStringArray(myCompletedMethodNames);
                myCompletedMethodNames.clear();
                processTracesAlarm.addRequest(() -> processAvailableTraces(fullTestNames, getTracesDirectory(configuration), moduleName, frameworkPrefix, TestDiscoveryIndex
                    .getInstance(configuration.getProject())), 100);
              }
            }
          }
        }

        @Override
        public void onTestingFinished(@Nonnull SMTestProxy.SMRootTestProxy testsRoot) {
          if (testsRoot.getHandler() == handler) {
            processTracesAlarm.cancelAllRequests();
            processTracesAlarm.addRequest(() ->
            {
              processAvailableTraces(configuration);
              Disposer.dispose(disposable);
            }, 0);
            connection.disconnect();
          }
        }
      });
    }
  }

  @Override
  public void updateJavaParameters(RunConfigurationBase configuration, OwnJavaParameters params, RunnerSettings runnerSettings) {
    if (runnerSettings != null || !isApplicableFor(configuration)) {
      return;
    }
    StringBuilder argument = new StringBuilder("-javaagent:");
    final String agentPath = ClassPathUtil.getJarPathForClass(ProjectData.class);//todo spaces
    argument.append(agentPath);
    params.getVMParametersList().add(argument.toString());
    params.getClassPath().add(agentPath);
    params.getVMParametersList().addProperty(ProjectData.TRACE_DIR, getTracesDirectory(configuration));
  }

  @Nonnull
  private static String getTracesDirectory(RunConfigurationBase configuration) {
    return baseTestDiscoveryPathForProject(configuration.getProject()) + File.separator + configuration.getUniqueID();
  }

  @Override
  public boolean isListenerDisabled(RunConfigurationBase configuration, Object listener, RunnerSettings runnerSettings) {
    return false;
    // FIXME [VISTALL] TestDiscoveryListener is not in root classloder
    //return listener instanceof TestDiscoveryListener && (runnerSettings != null || !isApplicableFor(configuration));
  }

  @Override
  public void readExternal(@Nonnull final RunConfigurationBase runConfiguration, @Nonnull Element element) throws InvalidDataException {
  }

  @Override
  public void writeExternal(@Nonnull RunConfigurationBase runConfiguration, @Nonnull Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }

  @Override
  protected boolean isApplicableFor(@Nonnull final RunConfigurationBase configuration) {
    return configuration instanceof JavaTestConfigurationBase && TESTDISCOVERY_ENABLED;
  }

  @Nonnull
  public static Path baseTestDiscoveryPathForProject(Project project) {
    return ProjectUtil.getProjectCachePath(project, "testDiscovery", true);
  }

  private static final Object ourTracesLock = new Object();

  private static void processAvailableTraces(RunConfigurationBase configuration) {
    final String tracesDirectory = getTracesDirectory(configuration);
    final TestDiscoveryIndex coverageIndex = TestDiscoveryIndex.getInstance(configuration.getProject());
    synchronized (ourTracesLock) {
      final File tracesDirectoryFile = new File(tracesDirectory);
      final File[] testMethodTraces = tracesDirectoryFile.listFiles((dir, name) -> name.endsWith(".tr"));
      if (testMethodTraces != null) {
        for (File testMethodTrace : testMethodTraces) {
          try {
            coverageIndex.updateFromTestTrace(testMethodTrace, ((JavaTestConfigurationBase) configuration).getConfigurationModule().getModuleName(), ((JavaTestConfigurationBase)
                configuration).getFrameworkPrefix());
            FileUtil.delete(testMethodTrace);
          } catch (IOException e) {
            LOG.error("Can not load " + testMethodTrace, e);
          }
        }

        final String[] filesInTracedDirectories = tracesDirectoryFile.list();
        if (filesInTracedDirectories == null || filesInTracedDirectories.length == 0) {
          FileUtil.delete(tracesDirectoryFile);
        }
      }
    }
  }

  @SuppressWarnings("WeakerAccess")
  // called via reflection from com.intellij.InternalTestDiscoveryListener.flushCurrentTraces()
  public static void processAvailableTraces(final String[] fullTestNames,
                                            final String tracesDirectory,
                                            final String moduleName,
                                            final String frameworkPrefix,
                                            final TestDiscoveryIndex discoveryIndex) {
    synchronized (ourTracesLock) {
      for (String fullTestName : fullTestNames) {
        final String className = StringUtil.getPackageName(fullTestName);
        final String methodName = StringUtil.getShortName(fullTestName);
        if (!StringUtil.isEmptyOrSpaces(className) && !StringUtil.isEmptyOrSpaces(methodName)) {
          final File testMethodTrace = new File(tracesDirectory, className + "-" + methodName + ".tr");
          if (testMethodTrace.exists()) {
            try {
              discoveryIndex.updateFromTestTrace(testMethodTrace, moduleName, frameworkPrefix);
              FileUtil.delete(testMethodTrace);
            } catch (Throwable e) {
              LOG.error("Can not load " + testMethodTrace, e);
            }
          }
        }
      }
    }

  }
}