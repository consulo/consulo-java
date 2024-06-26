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
package com.intellij.java.execution.impl;

import com.intellij.java.execution.configurations.JavaCommandLine;
import com.intellij.java.execution.configurations.JavaCommandLineState;
import com.intellij.java.execution.runners.JavaPatchableProgramRunner;
import com.intellij.java.execution.runners.ProcessProxy;
import com.intellij.java.execution.runners.ProcessProxyFactory;
import com.intellij.java.execution.unscramble.ThreadDumpParser;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.application.util.DateFormatUtil;
import consulo.document.FileDocumentManager;
import consulo.execution.ExecutionResult;
import consulo.execution.RunnerRegistry;
import consulo.execution.configuration.*;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.execution.runner.RunContentBuilder;
import consulo.execution.ui.ExecutionConsole;
import consulo.execution.ui.RunContentDescriptor;
import consulo.execution.unscramble.AnalyzeStacktraceUtil;
import consulo.execution.unscramble.ThreadDumpConsoleFactory;
import consulo.execution.unscramble.ThreadState;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.process.util.CapturingProcessAdapter;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.action.Presentation;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author spleaner
 */
@ExtensionImpl(id = "defaultJavaRunRunner")
public class DefaultJavaProgramRunner extends JavaPatchableProgramRunner {
  private final static String ourWiseThreadDumpProperty = "idea.java.run.wise.thread.dump";

  @NonNls
  public static final String DEFAULT_JAVA_RUNNER_ID = "Run";

  @Override
  public boolean canRun(@Nonnull final String executorId, @Nonnull final RunProfile profile) {
    return executorId.equals(DefaultRunExecutor.EXECUTOR_ID) && profile instanceof ModuleRunProfile
      && !(profile instanceof RunConfigurationWithSuppressedDefaultRunAction);
  }

  @Override
  public void patch(OwnJavaParameters javaParameters, RunnerSettings settings, RunProfile runProfile, final boolean beforeExecution) throws ExecutionException {
    runCustomPatchers(javaParameters, DefaultRunExecutor.getRunExecutorInstance(), runProfile);
  }

  protected RunContentDescriptor doExecute(@Nonnull RunProfileState state, @Nonnull ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();

    ExecutionResult executionResult;
    boolean shouldAddDefaultActions = true;
    if (state instanceof JavaCommandLine javaCommandLine) {
      final OwnJavaParameters parameters = javaCommandLine.getJavaParameters();
      patch(parameters, env.getRunnerSettings(), env.getRunProfile(), true);

      ProcessProxy proxy = ProcessProxyFactory.getInstance().createCommandLineProxy((JavaCommandLine) state);
      executionResult = state.execute(env.getExecutor(), this);
      if (proxy != null) {
        ProcessHandler handler = executionResult != null ? executionResult.getProcessHandler() : null;
        if (handler != null) {
          proxy.attach(handler);
          handler.addProcessListener(new ProcessAdapter() {
            @Override
            public void processTerminated(@Nonnull ProcessEvent event) {
              proxy.destroy();
            }
          });
        } else {
          proxy.destroy();
        }
      }

      if (state instanceof JavaCommandLineState javaCommandLineState && !javaCommandLineState.shouldAddJavaProgramRunnerActions()) {
        shouldAddDefaultActions = false;
      }
    } else {
      executionResult = state.execute(env.getExecutor(), this);
    }

    if (executionResult == null) {
      return null;
    }

    onProcessStarted(env.getRunnerSettings(), executionResult);

    final RunContentBuilder contentBuilder = new RunContentBuilder(executionResult, env);
    if (shouldAddDefaultActions) {
      addDefaultActions(contentBuilder, executionResult);
    }
    return contentBuilder.showRunContent(env.getContentToReuse());
  }

  private static void addDefaultActions(@Nonnull RunContentBuilder contentBuilder, @Nonnull ExecutionResult executionResult) {
    final ExecutionConsole executionConsole = executionResult.getExecutionConsole();
    final JComponent consoleComponent = executionConsole != null ? executionConsole.getComponent() : null;
    final ControlBreakAction controlBreakAction = new ControlBreakAction(executionResult.getProcessHandler());
    if (consoleComponent != null) {
      controlBreakAction.registerCustomShortcutSet(controlBreakAction.getShortcutSet(), consoleComponent);
      final ProcessHandler processHandler = executionResult.getProcessHandler();
      assert processHandler != null : executionResult;
      processHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@Nonnull final ProcessEvent event) {
          processHandler.removeProcessListener(this);
          controlBreakAction.unregisterCustomShortcutSet(consoleComponent);
        }
      });
    }
    contentBuilder.addAction(controlBreakAction);
    contentBuilder.addAction(new SoftExitAction(executionResult.getProcessHandler()));
  }


  private abstract static class ProxyBasedAction extends AnAction {
    protected final ProcessHandler myProcessHandler;

    protected ProxyBasedAction(String text, String description, Image icon, ProcessHandler processHandler) {
      super(text, description, icon);
      myProcessHandler = processHandler;
    }

    @RequiredUIAccess
    @Override
    public final void update(@Nonnull AnActionEvent event) {
      ProcessProxy proxy = ProcessProxyFactory.getInstance().getAttachedProxy(myProcessHandler);
      boolean available = proxy != null && available(proxy);
      Presentation presentation = event.getPresentation();
      if (!available) {
        presentation.setEnabledAndVisible(false);
      } else {
        presentation.setVisible(true);
        presentation.setEnabled(!myProcessHandler.isProcessTerminated());
      }
    }

    @RequiredUIAccess
    @Override
    public final void actionPerformed(@Nonnull AnActionEvent e) {
      ProcessProxy proxy = ProcessProxyFactory.getInstance().getAttachedProxy(myProcessHandler);
      if (proxy != null) {
        perform(e, proxy);
      }
    }

    protected abstract boolean available(ProcessProxy proxy);

    protected abstract void perform(AnActionEvent e, ProcessProxy proxy);
  }

  protected static class ControlBreakAction extends ProxyBasedAction {
    public ControlBreakAction(final ProcessHandler processHandler) {
      super(ExecutionLocalize.runConfigurationDumpThreadsActionName().get(), null, AllIcons.Actions.Dump, processHandler);
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_CANCEL, InputEvent.CTRL_DOWN_MASK)));
    }

    @Override
    protected boolean available(ProcessProxy proxy) {
      return proxy.canSendBreak();
    }

    @Override
    protected void perform(AnActionEvent e, ProcessProxy proxy) {
      boolean wise = Boolean.getBoolean(ourWiseThreadDumpProperty);
      WiseDumpThreadsListener wiseListener = wise ? new WiseDumpThreadsListener(e.getData(Project.KEY), myProcessHandler) : null;

      proxy.sendBreak();

      if (wiseListener != null) {                                           
        wiseListener.after();
      }
    }
  }

  private static class WiseDumpThreadsListener {
    private final Project myProject;
    private final ProcessHandler myProcessHandler;
    private final CapturingProcessAdapter myListener;

    public WiseDumpThreadsListener(final Project project, final ProcessHandler processHandler) {
      myProject = project;
      myProcessHandler = processHandler;

      myListener = new CapturingProcessAdapter();
      myProcessHandler.addProcessListener(myListener);
    }

    public void after() {
      if (myProject == null) {
        myProcessHandler.removeProcessListener(myListener);
        return;
      }
      myProject.getApplication().executeOnPooledThread((Runnable) () ->
      {
        if (myProcessHandler.isProcessTerminated() || myProcessHandler.isProcessTerminating()) {
          return;
        }
        List<ThreadState> threadStates = null;
        final long start = System.currentTimeMillis();
        while ((System.currentTimeMillis() - start) < 1000) {
          final String stdout = myListener.getOutput().getStdout();
          threadStates = ThreadDumpParser.parse(stdout);
          if (threadStates == null || threadStates.isEmpty()) {
            try {
              //noinspection BusyWait
              Thread.sleep(50);
            } catch (InterruptedException ignored) {
              //
            }
            threadStates = null;
            continue;
          }
          break;
        }
        myProcessHandler.removeProcessListener(myListener);
        if (threadStates != null && !threadStates.isEmpty()) {
          showThreadDump(myListener.getOutput().getStdout(), threadStates);
        }
      });
    }

    private void showThreadDump(final String out, final List<ThreadState> threadStates) {
      myProject.getApplication().invokeLater(
        () -> AnalyzeStacktraceUtil.addConsole(myProject, threadStates.size() > 1
          ? new ThreadDumpConsoleFactory(myProject, threadStates) : null,
          "<Stacktrace> " + DateFormatUtil.formatDateTime(System.currentTimeMillis()), out),
        myProject.getApplication().getNoneModalityState()
      );
    }
  }

  protected static class SoftExitAction extends ProxyBasedAction {
    public SoftExitAction(final ProcessHandler processHandler) {
      super(ExecutionLocalize.runConfigurationExitActionName().get(), null, AllIcons.Actions.Exit, processHandler);
    }

    @Override
    protected boolean available(ProcessProxy proxy) {
      return proxy.canSendStop();
    }

    @Override
    protected void perform(AnActionEvent e, ProcessProxy proxy) {
      proxy.sendStop();
    }
  }

  @Override
  @Nonnull
  public String getRunnerId() {
    return DEFAULT_JAVA_RUNNER_ID;
  }

  public static ProgramRunner getInstance() {
    return RunnerRegistry.getInstance().findRunnerById(DEFAULT_JAVA_RUNNER_ID);
  }
}
