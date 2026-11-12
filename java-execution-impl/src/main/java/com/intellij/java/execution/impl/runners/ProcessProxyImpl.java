// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution.impl.runners;

import com.intellij.java.execution.runners.ProcessProxy;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.platform.PlatformServerSocket;
import consulo.platform.PlatformSocket;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerFeature;
import consulo.util.dataholder.Key;
import consulo.util.lang.function.ThrowableRunnable;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The launcher agent in the process connects back to a port of the machine of the process - the local one, or another
 * platform, where the port is bound through the platform.
 */
class ProcessProxyImpl implements ProcessProxy {
  static final Key<ProcessProxyImpl> KEY = Key.create("ProcessProxyImpl");

  private final Platform myPlatform;
  private final PlatformServerSocket myServerSocket;
  private final @Nullable Path myBreakgenLibrary;

  private final Object myLock = new Object();
  private @Nullable PlatformSocket myConnection;
  private ProcessHandlerFeature.@Nullable POSIX myPosixFeature;

  /**
   * @param breakgenLibrary the native helper of the process on windows - a file of the machine of the process
   */
  ProcessProxyImpl(String mainClass, Platform platform, @Nullable Path breakgenLibrary) throws IOException {
    myPlatform = platform;
    myBreakgenLibrary = breakgenLibrary;
    myServerSocket = platform.net().bind("127.0.0.1", 0);

    Thread.ofVirtual().name("Process Proxy: " + mainClass).start(() -> {
      try {
        PlatformSocket connection = myServerSocket.accept();
        synchronized (myLock) {
          myConnection = connection;
        }
      }
      catch (IOException ignored) {
        // closed before the process connected
      }
    });
  }

  int getPortNumber() {
    return myServerSocket.getPort();
  }

  @Override
  public void attach(ProcessHandler processHandler) {
    processHandler.putUserData(KEY, this);
    execute(() ->
    {
      ProcessHandlerFeature.POSIX posix = processHandler.getFeature(ProcessHandlerFeature.POSIX.class);
      synchronized (myLock) {
        myPosixFeature = posix;
      }
    });
  }

  private void writeLine(String s) {
    execute(() ->
    {
      synchronized (myLock) {
        if (myConnection != null) {
          OutputStream out = myConnection.getOutputStream();
          out.write((s + '\n').getBytes(StandardCharsets.US_ASCII));
          out.flush();
        }
      }
    });
  }

  @Override
  public boolean canSendBreak() {
    if (myPlatform.os().isWindows()) {
      synchronized (myLock) {
        if (myConnection == null) {
          return false;
        }
      }
      return myBreakgenLibrary != null && Files.exists(myBreakgenLibrary);
    }

    synchronized (myLock) {
      return myPosixFeature != null;
    }
  }

  @Override
  public boolean canSendStop() {
    synchronized (myLock) {
      return myConnection != null;
    }
  }

  @Override
  public void sendBreak() {
    if (myPlatform.os().isWindows()) {
      writeLine("BREAK");
    } else {
      ProcessHandlerFeature.POSIX posix;
      synchronized (myLock) {
        posix = myPosixFeature;
      }

      if (posix != null) {
        posix.sendSignal(ProcessHandlerFeature.POSIX.SIGQUIT);
      }
    }
  }

  @Override
  public void sendStop() {
    writeLine("STOP");
  }

  @Override
  public void destroy() {
    execute(() ->
    {
      synchronized (myLock) {
        if (myConnection != null) {
          myConnection.close();
        }
      }
    });
    execute(myServerSocket::close);
  }

  private static void execute(ThrowableRunnable<Exception> block) {
    try {
      block.run();
    } catch (Exception e) {
      Logger.getInstance(ProcessProxy.class).warn(e);
    }
  }
}
