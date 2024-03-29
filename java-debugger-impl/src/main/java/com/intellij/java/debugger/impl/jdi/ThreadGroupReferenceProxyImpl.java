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
package com.intellij.java.debugger.impl.jdi;

import com.intellij.java.debugger.engine.jdi.ThreadGroupReferenceProxy;
import consulo.logging.Logger;
import consulo.internal.com.sun.jdi.ThreadGroupReference;
import consulo.internal.com.sun.jdi.ThreadReference;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ThreadGroupReferenceProxyImpl extends ObjectReferenceProxyImpl implements ThreadGroupReferenceProxy{
  private static final Logger LOG = Logger.getInstance(ThreadGroupReferenceProxyImpl.class);
  //caches
  private ThreadGroupReferenceProxyImpl myParentThreadGroupProxy;
  private boolean myIsParentGroupCached = false;
  private String myName;

  public ThreadGroupReferenceProxyImpl(VirtualMachineProxyImpl virtualMachineProxy, ThreadGroupReference threadGroupReference) {
    super(virtualMachineProxy, threadGroupReference);
  }

  public ThreadGroupReference getThreadGroupReference() {
    return (ThreadGroupReference)getObjectReference();
  }

  public String name() {
    checkValid();
    if (myName == null) {
      myName = getThreadGroupReference().name();
    }
    return myName;
  }

  public ThreadGroupReferenceProxyImpl parent() {
    checkValid();
    if (!myIsParentGroupCached) {
      myParentThreadGroupProxy = getVirtualMachineProxy().getThreadGroupReferenceProxy(getThreadGroupReference().parent());
      myIsParentGroupCached = true;
    }
    return myParentThreadGroupProxy;
  }

  public @NonNls String toString() {
    return "ThreadGroupReferenceProxy: " + getThreadGroupReference().toString();
  }

  public void suspend() {
    getThreadGroupReference().suspend();
  }

  public void resume() {
    getThreadGroupReference().resume();
  }

  public List<ThreadReferenceProxyImpl> threads() {
    List<ThreadReference> list = getThreadGroupReference().threads();
    List<ThreadReferenceProxyImpl> proxies = new ArrayList<ThreadReferenceProxyImpl>(list.size());

    for (Iterator<ThreadReference> iterator = list.iterator(); iterator.hasNext();) {
      ThreadReference threadReference = iterator.next();
      proxies.add(getVirtualMachineProxy().getThreadReferenceProxy(threadReference));
    }
    return proxies;
  }

  public List<ThreadGroupReferenceProxyImpl> threadGroups() {
    List<ThreadGroupReference> list = getThreadGroupReference().threadGroups();
    List<ThreadGroupReferenceProxyImpl> proxies = new ArrayList<ThreadGroupReferenceProxyImpl>(list.size());

    for (Iterator<ThreadGroupReference> iterator = list.iterator(); iterator.hasNext();) {
      ThreadGroupReference threadGroupReference = iterator.next();
      proxies.add(getVirtualMachineProxy().getThreadGroupReferenceProxy(threadGroupReference));
    }
    return proxies;
  }

  public void clearCaches() {
//    myIsParentGroupCached = false;
//    myName = null;
//    myParentThreadGroupProxy = null;
    super.clearCaches();
  }
}
