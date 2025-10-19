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

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import com.intellij.java.debugger.impl.ui.tree.ThreadGroupDescriptor;
import com.intellij.java.debugger.impl.ui.tree.render.DescriptorLabelListener;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.internal.com.sun.jdi.ObjectCollectedException;
import consulo.localize.LocalizeValue;

public class ThreadGroupDescriptorImpl extends NodeDescriptorImpl implements ThreadGroupDescriptor{
  private final ThreadGroupReferenceProxyImpl myThreadGroup;
  private boolean myIsCurrent;
  private String myName = null;
  private boolean myIsExpandable = true;

  public ThreadGroupDescriptorImpl(ThreadGroupReferenceProxyImpl threadGroup) {
    myThreadGroup = threadGroup;
  }

  @Override
  public ThreadGroupReferenceProxyImpl getThreadGroupReference() {
    return myThreadGroup;
  }

  public boolean isCurrent() {
    return myIsCurrent;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  protected LocalizeValue calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    ThreadGroupReferenceProxyImpl group = getThreadGroupReference();
    try {
      myName = group.name();
      return JavaDebuggerLocalize.labelThreadGroupNode(myName, group.uniqueID());
    }
    catch (ObjectCollectedException e) {
      return myName != null ? JavaDebuggerLocalize.labelThreadGroupNodeGroupCollected(myName) : LocalizeValue.empty();
    }
  }

  @Override
  public boolean isExpandable() {
    return myIsExpandable;
  }

  @Override
  public void setContext(EvaluationContextImpl context) {
    ThreadReferenceProxyImpl threadProxy = context != null? context.getSuspendContext().getThread() : null;
    myIsCurrent = threadProxy != null && isDescendantGroup(threadProxy.threadGroupProxy());
    myIsExpandable = calcExpandable();
  }

  private boolean isDescendantGroup(ThreadGroupReferenceProxyImpl group) {
    if(group == null) return false;

    if(getThreadGroupReference() == group) return true;

    return isDescendantGroup(group.parent());
  }

  private boolean calcExpandable() {
    ThreadGroupReferenceProxyImpl group = getThreadGroupReference();
    return group.threads().size() > 0 || group.threadGroups().size() > 0;
  }
}