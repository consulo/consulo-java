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
package com.intellij.java.execution.impl;

import com.intellij.java.debugger.DebuggerManager;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.impl.DebugEnvironment;
import com.intellij.java.debugger.impl.DefaultDebugEnvironment;
import com.intellij.java.debugger.impl.GenericDebuggerRunner;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.execution.configurations.RemoteConnection;
import consulo.execution.configuration.RunProfile;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.debug.DefaultDebugExecutor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.RunContentDescriptor;
import consulo.process.ExecutionException;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class JavaTestFrameworkDebuggerRunner extends GenericDebuggerRunner {
  @Nonnull
  @Override
  public abstract String getRunnerId();

  protected abstract boolean validForProfile(@Nonnull RunProfile profile);

  @Nonnull
  protected abstract String getThreadName();

  @Override
  public boolean canRun(@Nonnull String executorId, @Nonnull RunProfile profile) {
    return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && validForProfile(profile);
  }

  @Nullable
  @Override
  protected RunContentDescriptor createContentDescriptor(@Nonnull final RunProfileState state, @Nonnull final ExecutionEnvironment environment) throws ExecutionException {
    final RunContentDescriptor res = super.createContentDescriptor(state, environment);
    final ServerSocket socket = ((JavaTestFrameworkRunnableState) state).getForkSocket();
    if (socket != null) {
      Thread thread = new Thread(getThreadName() + " debugger runner") {
        @Override
        public void run() {
          try {
            final Socket accept = socket.accept();
            try {
              DataInputStream stream = new DataInputStream(accept.getInputStream());
              try {
                int read = stream.readInt();
                while (read != -1) {
                  final DebugProcess process = DebuggerManager.getInstance(environment.getProject()).getDebugProcess(res.getProcessHandler());
                  if (process == null) {
                    break;
                  }
                  final RemoteConnection connection = new RemoteConnection(true, "127.0.0.1", String.valueOf(read), true);
                  final DebugEnvironment env = new DefaultDebugEnvironment(environment, state, connection, true);
                  SwingUtilities.invokeLater(() ->
                  {
                    try {
                      ((DebugProcessImpl) process).reattach(env);
                      accept.getOutputStream().write(0);
                    } catch (Exception e) {
                      e.printStackTrace();
                    }
                  });
                  read = stream.readInt();
                }
              } finally {
                stream.close();
              }
            } finally {
              accept.close();
            }
          } catch (EOFException ignored) {
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      };
      thread.setDaemon(true);
      thread.start();
    }
    return res;
  }
}
