package com.intellij.java.debugger.impl;

import consulo.annotation.component.ExtensionImpl;

@ExtensionImpl
public class HotSwapManagerListeners implements DebuggerManagerListener {
  @Override
  public void sessionCreated(DebuggerSession session) {
    HotSwapManager manager = HotSwapManager.getInstance(session.getProject());
    manager.getTimeStamps().put(session, System.currentTimeMillis());
  }

  @Override
  public void sessionRemoved(DebuggerSession session) {
    HotSwapManager manager = HotSwapManager.getInstance(session.getProject());
    manager.getTimeStamps().remove(session);
  }
}
