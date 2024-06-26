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
package com.intellij.java.execution.impl.testframework;

import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.component.ProcessCanceledException;
import consulo.execution.localize.ExecutionLocalize;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class SearchForTestsTask extends Task.Backgroundable {
  private static final Logger LOG = Logger.getInstance(SearchForTestsTask.class);
  protected Socket mySocket;
  private ServerSocket myServerSocket;
  private ProgressIndicator myProcessIndicator;

  public SearchForTestsTask(@Nullable final Project project, final ServerSocket socket) {
    super(project, ExecutionLocalize.searchingTestProgressTitle().get(), true);
    myServerSocket = socket;
  }

  protected abstract void search() throws ExecutionException;

  protected abstract void onFound() throws ExecutionException;

  public void ensureFinished() {
    if (myProcessIndicator != null && !myProcessIndicator.isCanceled()) {
      finish();
    }
  }

  public void startSearch() {
    if (Application.get().isUnitTestMode()) {
      try {
        search();
      } catch (Throwable e) {
        LOG.error(e);
      }
      try {
        onFound();
      } catch (ExecutionException e) {
        LOG.error(e);
      }
    } else {
      queue();
    }
  }

  public void attachTaskToProcess(final ProcessHandler handler) {
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@Nonnull final ProcessEvent event) {
        handler.removeProcessListener(this);
        ensureFinished();
      }

      @Override
      public void startNotified(@Nonnull final ProcessEvent event) {
        startSearch();
      }
    });
  }

  @Override
  public void run(@Nonnull ProgressIndicator indicator) {
    try {
      myProcessIndicator = indicator;
      mySocket = myServerSocket.accept();
      final ExecutionException[] ex = new ExecutionException[1];
      Runnable runnable = () ->
      {
        try {
          search();
        } catch (ExecutionException e) {
          ex[0] = e;
        }
      };
      while (!run(runnable, indicator)) {
        ;
      }
      if (ex[0] != null) {
        logCantRunException(ex[0]);
      }
    } catch (ProcessCanceledException e) {
      throw e;
    } catch (IOException e) {
      LOG.info(e);
    } catch (Throwable e) {
      LOG.error(e);
    }
  }

  /**
   * @return true if runnable has been executed with no write action interference and in "smart" mode
   */
  private boolean run(@Nonnull Runnable runnable, ProgressIndicator indicator) {
    DumbService dumbService = DumbService.getInstance((Project) myProject);

    indicator.checkCanceled();
    dumbService.waitForSmartMode();

    if (myProject.isDisposed()) {
      return true;
    }

    if (dumbService.isDumb()) {
      Thread.onSpinWait();
      return false;
    }

    runnable.run();
    return true;
  }

  protected void logCantRunException(ExecutionException e) throws ExecutionException {
    throw e;
  }

  @Override
  @RequiredUIAccess
  public void onCancel() {
    finish();
  }

  @Override
  @RequiredUIAccess
  public void onSuccess() {
    Runnable runnable = () ->
    {
      try {
        onFound();
      } catch (ExecutionException e) {
        LOG.error(e);
      }
      finish();
    };
    DumbService.getInstance((Project) getProject()).runWhenSmart(runnable);
  }

  public void finish() {
    DataOutputStream os = null;
    try {
      if (mySocket == null || mySocket.isClosed()) {
        return;
      }
      os = new DataOutputStream(mySocket.getOutputStream());
      os.writeBoolean(true);
    } catch (Throwable e) {
      LOG.info(e);
    } finally {
      try {
        if (os != null) {
          os.close();
        }
      } catch (Throwable e) {
        LOG.info(e);
      }

      try {
        if (!myServerSocket.isClosed()) {
          myServerSocket.close();
        }
      } catch (Throwable e) {
        LOG.info(e);
      }
    }
  }
}
