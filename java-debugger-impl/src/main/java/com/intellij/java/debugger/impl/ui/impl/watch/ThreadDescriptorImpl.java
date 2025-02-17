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
package com.intellij.java.debugger.impl.ui.impl.watch;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.engine.SuspendManager;
import com.intellij.java.debugger.impl.engine.SuspendManagerUtil;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import com.intellij.java.debugger.impl.ui.tree.ThreadDescriptor;
import com.intellij.java.debugger.impl.ui.tree.render.DescriptorLabelListener;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.internal.com.sun.jdi.ObjectCollectedException;
import consulo.internal.com.sun.jdi.ThreadReference;
import consulo.ui.image.Image;

public class ThreadDescriptorImpl extends NodeDescriptorImpl implements ThreadDescriptor {
    private final ThreadReferenceProxyImpl myThread;
    private String myName = null;
    private boolean myIsExpandable = true;
    private boolean myIsSuspended = false;
    private boolean myIsCurrent;
    private boolean myIsFrozen;

    private boolean myIsAtBreakpoint;
    private SuspendContextImpl mySuspendContext;

    public ThreadDescriptorImpl(ThreadReferenceProxyImpl thread) {
        myThread = thread;
    }

    @Override
    public String getName() {
        return myName;
    }

    @Override
    protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) throws EvaluateException {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        ThreadReferenceProxyImpl thread = getThreadReference();
        try {
            myName = thread.name();
            ThreadGroupReferenceProxyImpl gr = getThreadReference().threadGroupProxy();
            final String grname = (gr != null) ? gr.name() : null;
            final String threadStatusText = DebuggerUtilsEx.getThreadStatusText(getThreadReference().status());
            //noinspection HardCodedStringLiteral
            if (grname != null && !"SYSTEM".equalsIgnoreCase(grname)) {
                return DebuggerBundle.message("label.thread.node.in.group", myName, thread.uniqueID(), threadStatusText, grname);
            }
            return DebuggerBundle.message("label.thread.node", myName, thread.uniqueID(), threadStatusText);
        }
        catch (ObjectCollectedException e) {
            return myName != null ? DebuggerBundle.message("label.thread.node.thread.collected", myName) : "";
        }
    }

    @Override
    public ThreadReferenceProxyImpl getThreadReference() {
        return myThread;
    }

    public boolean isCurrent() {
        return myIsCurrent;
    }

    public boolean isFrozen() {
        return myIsFrozen;
    }

    @Override
    public boolean isExpandable() {
        return myIsExpandable;
    }

    @Override
    public void setContext(EvaluationContextImpl context) {
        final ThreadReferenceProxyImpl thread = getThreadReference();
        final SuspendManager suspendManager = context != null ? context.getDebugProcess().getSuspendManager() : null;
        final SuspendContextImpl suspendContext = context != null ? context.getSuspendContext() : null;

        try {
            myIsSuspended = suspendManager != null ? suspendManager.isSuspended(thread) : thread.isSuspended();
        }
        catch (ObjectCollectedException e) {
            myIsSuspended = false;
        }
        myIsExpandable = calcExpandable(myIsSuspended);
        mySuspendContext = SuspendManagerUtil.getSuspendContextForThread(suspendContext, thread);
        myIsAtBreakpoint = suspendManager != null ? SuspendManagerUtil.findContextByThread(suspendManager, thread) != null : thread.getThreadReference().isAtBreakpoint();
        myIsCurrent = suspendContext != null ? suspendContext.getThread() == thread : false;
        myIsFrozen = suspendManager != null ? suspendManager.isFrozen(thread) : myIsSuspended;
    }

    private boolean calcExpandable(final boolean isSuspended) {
        if (!isSuspended) {
            return false;
        }
        final int status = getThreadReference().status();
        if (status == ThreadReference.THREAD_STATUS_UNKNOWN ||
            status == ThreadReference.THREAD_STATUS_NOT_STARTED ||
            status == ThreadReference.THREAD_STATUS_ZOMBIE) {
            return false;
        }
        return true;
    /*
    // [jeka] with lots of threads calling threadProxy.frameCount() in advance while setting context can be costly....
    // see IDEADEV-2020
    try {
      return threadProxy.frameCount() > 0;
    }
    catch (EvaluateException e) {
      //LOG.assertTrue(false);
      // if we pause during evaluation of this method the exception is thrown
      //  private static void longMethod(){
      //    try {
      //      Thread.sleep(100000);
      //    } catch (InterruptedException e) {
      //      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      //    }
      //  }
      return false;
    }
    */
    }

    public SuspendContextImpl getSuspendContext() {
        return mySuspendContext;
    }

    public boolean isAtBreakpoint() {
        return myIsAtBreakpoint;
    }

    public boolean isSuspended() {
        return myIsSuspended;
    }

    public Image getIcon() {
        if (isCurrent()) {
            return ExecutionDebugIconGroup.threadThreadcurrent();
        }
        if (isFrozen()) {
            return ExecutionDebugIconGroup.threadThreadfrozen();
        }
        if (isAtBreakpoint()) {
            return ExecutionDebugIconGroup.threadThreadatbreakpoint();
        }
        if (isSuspended()) {
            return ExecutionDebugIconGroup.threadThreadsuspended();
        }
        return ExecutionDebugIconGroup.threadThreadrunning();
    }
}
