// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution.impl.runners;

import com.intellij.java.execution.runners.ProcessProxy;
import consulo.container.plugin.PluginManager;
import consulo.java.language.module.util.JavaClassNames;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerFeature;
import consulo.util.dataholder.Key;
import consulo.util.lang.function.ThrowableRunnable;
import jakarta.annotation.Nonnull;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.TimeUnit;

/**
 * @author ven
 */
class ProcessProxyImpl implements ProcessProxy {
  static final Key<ProcessProxyImpl> KEY = Key.create("ProcessProxyImpl");

  private final AsynchronousChannelGroup myGroup;
  private final int myPort;

  private final Object myLock = new Object();
  private AsynchronousSocketChannel myConnection;
  private ProcessHandlerFeature.POSIX myPosixFeature;

  ProcessProxyImpl(String mainClass) throws IOException {
    myGroup = AsynchronousChannelGroup.withFixedThreadPool(1, r -> new Thread(r, "Process Proxy: " + mainClass));
    AsynchronousServerSocketChannel channel = AsynchronousServerSocketChannel.open(myGroup).bind(new InetSocketAddress("127.0.0.1", 0)).setOption(StandardSocketOptions.SO_REUSEADDR, true);
    myPort = ((InetSocketAddress) channel.getLocalAddress()).getPort();

    channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
      @Override
      public void completed(AsynchronousSocketChannel channel, Void attachment) {
        synchronized (myLock) {
          myConnection = channel;
        }
      }

      @Override
      public void failed(Throwable t, Void attachment) {
      }
    });
  }

  int getPortNumber() {
    return myPort;
  }

  @Override
  public void attach(@Nonnull ProcessHandler processHandler) {
    processHandler.putUserData(KEY, this);
    execute(() ->
    {
      int pid = (int) processHandler.getId();
      ProcessHandlerFeature.POSIX posix = processHandler.getFeature(ProcessHandlerFeature.POSIX.class);
      synchronized (myLock) {
        myPosixFeature = posix;
      }
    });
  }

  private void writeLine(String s) {
    execute(() ->
    {
      ByteBuffer out = ByteBuffer.wrap((s + '\n').getBytes("US-ASCII"));
      synchronized (myLock) {
        myConnection.write(out);
      }
    });
  }

  @Override
  public boolean canSendBreak() {
    if (Platform.current().os().isWindows()) {
      synchronized (myLock) {
        if (myConnection == null) {
          return false;
        }
      }
      return new File(PluginManager.getPluginPath(JavaClassNames.class), "breakgen/breakgen.dll").exists();
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
    if (Platform.current().os().isWindows()) {
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
    execute(() ->
    {
      myGroup.shutdownNow();
      myGroup.awaitTermination(1, TimeUnit.SECONDS);
    });
  }

  private static void execute(ThrowableRunnable<Exception> block) {
    try {
      block.run();
    } catch (Exception e) {
      Logger.getInstance(ProcessProxy.class).warn(e);
    }
  }
}