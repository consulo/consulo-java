package com.intellij.java.debugger.impl.ui;

import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.DebuggerManagerListener;
import com.intellij.java.debugger.impl.DebuggerSession;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicImpl;
import consulo.compiler.CompileContext;
import consulo.compiler.event.CompilationStatusListener;
import consulo.component.messagebus.MessageBusConnection;
import consulo.project.Project;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@TopicImpl(ComponentScope.PROJECT)
public class HotSwapUIImplListeners implements DebuggerManagerListener {

  private static class MyCompilationStatusListener implements CompilationStatusListener {
    private Project myProject;

    public MyCompilationStatusListener(Project project) {
      myProject = project;
    }

    @Override
    public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
      if (myProject.isDisposed()) {
        return;
      }

      HotSwapUIImpl hotSwapUI = (HotSwapUIImpl)HotSwapUI.getInstance(myProject);

      if (errors == 0 && !aborted && hotSwapUI.myPerformHotswapAfterThisCompilation) {
        for (HotSwapVetoableListener listener : hotSwapUI.myListeners) {
          if (!listener.shouldHotSwap(compileContext)) {
            return;
          }
        }

        final List<DebuggerSession> sessions = new ArrayList<>();
        Collection<DebuggerSession> debuggerSessions = DebuggerManagerEx.getInstanceEx(myProject).getSessions();
        for (final DebuggerSession debuggerSession : debuggerSessions) {
          if (debuggerSession.isAttached() && debuggerSession.getProcess().canRedefineClasses()) {
            sessions.add(debuggerSession);
          }
        }
        if (!sessions.isEmpty()) {
          hotSwapUI.hotSwapSessions(sessions);
        }
      }
      hotSwapUI.myPerformHotswapAfterThisCompilation = true;
    }
  }

  private MessageBusConnection myConn = null;
  private int mySessionCount = 0;

  @Override
  public void sessionAttached(DebuggerSession session) {
    if (mySessionCount++ == 0) {
      Project project = session.getProject();

      myConn = project.getMessageBus().connect();
      myConn.subscribe(CompilationStatusListener.class, new MyCompilationStatusListener(project));
    }
  }

  @Override
  public void sessionDetached(DebuggerSession session) {
    mySessionCount = Math.max(0, mySessionCount - 1);
    if (mySessionCount == 0) {
      final MessageBusConnection conn = myConn;
      if (conn != null) {
        conn.disconnect();
        myConn = null;
      }
    }
  }
}
