/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.java.debugger.impl.actions;

import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.ActionsBundle;
import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.jdi.VirtualMachineProxyImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.*;
import consulo.ui.ex.action.AnActionEvent;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.internal.com.sun.jdi.InvalidStackFrameException;
import consulo.internal.com.sun.jdi.NativeMethodException;
import consulo.internal.com.sun.jdi.VMDisconnectedException;

import consulo.ui.ex.awt.UIUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class PopFrameAction extends DebuggerAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(Project.KEY);
    StackFrameProxyImpl stackFrame = getStackFrameProxy(e);
    if (stackFrame == null) {
      return;
    }
    try {
      DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
      DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
      if (debugProcess == null) {
        return;
      }
      debugProcess.getManagerThread().schedule(debugProcess.createPopFrameCommand(debuggerContext, stackFrame));
    }
    catch (NativeMethodException e2) {
      Messages.showMessageDialog(
        project,
        DebuggerBundle.message("error.native.method.exception"),
        ActionsBundle.actionText(DebuggerActions.POP_FRAME),
        UIUtil.getErrorIcon()
      );
    }
    catch (InvalidStackFrameException ignored) {
    }
    catch (VMDisconnectedException vde) {
    }
  }

  @Nullable
  private static StackFrameProxyImpl getStackFrameProxy(AnActionEvent e) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if (selectedNode != null) {
      NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
      if (descriptor instanceof StackFrameDescriptorImpl stackFrameDescriptor) {
        if (selectedNode.getNextSibling() != null) {
          StackFrameDescriptorImpl frameDescriptor = stackFrameDescriptor;
          return frameDescriptor.getFrameProxy();
        }
        return null;
      }
      else if (descriptor instanceof ThreadDescriptorImpl || descriptor instanceof ThreadGroupDescriptorImpl) {
        return null;
      }
    }
    DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    StackFrameProxyImpl frameProxy = debuggerContext.getFrameProxy();

    if (frameProxy == null || frameProxy.isBottom()) {
      return null;
    }
    return frameProxy;
  }

  private static boolean isAtBreakpoint(AnActionEvent e) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if (selectedNode != null && selectedNode.getDescriptor() instanceof StackFrameDescriptorImpl) {
      DebuggerTreeNodeImpl parent = selectedNode.getParent();
      if (parent != null) {
        return ((ThreadDescriptorImpl)parent.getDescriptor()).isAtBreakpoint();
      }
    }
    DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    SuspendContextImpl suspendContext = debuggerContext.getSuspendContext();
    return suspendContext != null && debuggerContext.getThreadProxy() == suspendContext.getThread();
  }

  public void update(@Nonnull AnActionEvent e) {
    boolean enable = false;
    StackFrameProxyImpl stackFrameProxy = getStackFrameProxy(e);

    if (stackFrameProxy != null && isAtBreakpoint(e)) {
      VirtualMachineProxyImpl virtualMachineProxy = stackFrameProxy.getVirtualMachine();
      enable = virtualMachineProxy.canPopFrames();
    }

    if (ActionPlaces.MAIN_MENU.equals(e.getPlace()) || ActionPlaces.DEBUGGER_TOOLBAR.equals(e.getPlace())) {
      e.getPresentation().setEnabled(enable);
    }
    else {
      e.getPresentation().setVisible(enable);
    }
  }
}
