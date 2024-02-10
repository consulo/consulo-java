/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.ui;

import com.intellij.java.debugger.impl.*;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.JavaDebugProcess;
import com.intellij.java.debugger.impl.ui.tree.render.BatchEvaluator;
import com.intellij.java.execution.configurations.RemoteConnection;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.debug.*;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.RunContentDescriptor;
import consulo.execution.ui.event.RunContentWithExecutorListener;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@Singleton
@ServiceAPI(value = ComponentScope.PROJECT, lazy = false)
@ServiceImpl
public class DebuggerPanelsManager {
  private final Project myProject;

  @Inject
  public DebuggerPanelsManager(Project project) {
    myProject = project;
    if (project.isDefault()) {
      return;
    }

    myProject.getMessageBus().connect(myProject).subscribe(RunContentWithExecutorListener.class, new RunContentWithExecutorListener() {
      @Override
      public void contentSelected(@Nullable RunContentDescriptor descriptor, @Nonnull Executor executor) {
        if (executor == DefaultDebugExecutor.getDebugExecutorInstance()) {
          DebuggerSession session = descriptor == null ? null : getSession(myProject, descriptor);
          if (session != null) {
            getContextManager().setState(session.getContextManager().getContext(), session.getState(), DebuggerSession.Event.CONTEXT, null);
          }
          else {
            getContextManager().setState(DebuggerContextImpl.EMPTY_CONTEXT,
                                         DebuggerSession.State.DISPOSED,
                                         DebuggerSession.Event.CONTEXT,
                                         null);
          }
        }
      }

      @Override
      public void contentRemoved(@Nullable RunContentDescriptor descriptor, @Nonnull Executor executor) {
      }
    });
  }

  private DebuggerStateManager getContextManager() {
    return DebuggerManagerEx.getInstanceEx(myProject).getContextManager();
  }

  @Nullable
  public RunContentDescriptor attachVirtualMachine(@Nonnull ExecutionEnvironment environment,
                                                   RunProfileState state,
                                                   RemoteConnection remoteConnection,
                                                   boolean pollConnection) throws ExecutionException {
    return attachVirtualMachine(new DefaultDebugUIEnvironment(environment, state, remoteConnection, pollConnection));
  }

  @Nullable
  public RunContentDescriptor attachVirtualMachine(DebugUIEnvironment environment) throws ExecutionException {
    final DebugEnvironment modelEnvironment = environment.getEnvironment();
    final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(modelEnvironment);
    if (debuggerSession == null) {
      return null;
    }

    final DebugProcessImpl debugProcess = debuggerSession.getProcess();
    if (debugProcess.isDetached() || debugProcess.isDetaching()) {
      debuggerSession.dispose();
      return null;
    }
    if (modelEnvironment.isRemote()) {
      // optimization: that way BatchEvaluator will not try to lookup the class file in remote VM
      // which is an expensive operation when executed first time
      debugProcess.putUserData(BatchEvaluator.REMOTE_SESSION_KEY, Boolean.TRUE);
    }

    XDebugSession debugSession = XDebuggerManager.getInstance(myProject)
                                                 .startSessionAndShowTab(modelEnvironment.getSessionName(),
                                                                         environment.getReuseContent(),
                                                                         new XDebugProcessStarter() {
                                                                           @Override
                                                                           @Nonnull
                                                                           public XDebugProcess start(@Nonnull XDebugSession session) {
                                                                             return JavaDebugProcess.create(session, debuggerSession);
                                                                           }
                                                                         });
    return debugSession.getRunContentDescriptor();
  }

  public static DebuggerPanelsManager getInstance(Project project) {
    return project.getComponent(DebuggerPanelsManager.class);
  }

  private static DebuggerSession getSession(Project project, RunContentDescriptor descriptor) {
    for (JavaDebugProcess process : XDebuggerManager.getInstance(project).getDebugProcesses(JavaDebugProcess.class)) {
      if (Comparing.equal(process.getProcessHandler(), descriptor.getProcessHandler())) {
        return process.getDebuggerSession();
      }
    }
    return null;
  }
}
