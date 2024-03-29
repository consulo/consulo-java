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

import consulo.internal.com.sun.jdi.*;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ObjectReferenceProxyImpl extends JdiProxy {
  private final ObjectReference myObjectReference;

  //caches
  private ReferenceType myReferenceType;
  private Type myType;
  private Boolean myIsCollected = null;

  public ObjectReferenceProxyImpl(VirtualMachineProxyImpl virtualMachineProxy, @Nonnull ObjectReference objectReference) {
    super(virtualMachineProxy);
    myObjectReference = objectReference;
  }

  public ObjectReference getObjectReference() {
    checkValid();
    return myObjectReference;
  }

  public VirtualMachineProxyImpl getVirtualMachineProxy() {
    return (VirtualMachineProxyImpl) myTimer;
  }

  public ReferenceType referenceType() {
    checkValid();
    if (myReferenceType == null) {
      myReferenceType = getObjectReference().referenceType();
    }
    return myReferenceType;
  }

  public Type type() {
    checkValid();
    if (myType == null) {
      myType = getObjectReference().type();
    }
    return myType;
  }

  @NonNls
  public String toString() {
    final ObjectReference objectReference = getObjectReference();
    //noinspection HardCodedStringLiteral
    final String objRefString = objectReference != null? objectReference.toString() : "[referenced object collected]";
    return "ObjectReferenceProxyImpl: " + objRefString + " " + super.toString();
  }

  public Map<Field, Value> getValues(List<? extends Field> list) {
    return getObjectReference().getValues(list);
  }

  public void setValue(Field field, Value value) throws InvalidTypeException, ClassNotLoadedException {
    getObjectReference().setValue(field, value);
  }

  public boolean isCollected() {
    checkValid();
    if (myIsCollected == null || Boolean.FALSE.equals(myIsCollected)) {
      try {
        myIsCollected = Boolean.valueOf(VirtualMachineProxyImpl.isCollected(myObjectReference));
      }
      catch (VMDisconnectedException e) {
        myIsCollected = Boolean.TRUE;
      }
    }
    return myIsCollected.booleanValue();
  }

  public long uniqueID() {
    return getObjectReference().uniqueID();
  }

  /**
   * @return a list of waiting ThreadReferenceProxies
   * @throws IncompatibleThreadStateException
   */
  public List<ThreadReferenceProxyImpl> waitingThreads() throws IncompatibleThreadStateException {
    List<ThreadReference> list = getObjectReference().waitingThreads();
    List<ThreadReferenceProxyImpl> proxiesList = new ArrayList<ThreadReferenceProxyImpl>(list.size());

    for (ThreadReference threadReference : list) {
      proxiesList.add(getVirtualMachineProxy().getThreadReferenceProxy(threadReference));
    }
    return proxiesList;
  }

  public ThreadReferenceProxyImpl owningThread() throws IncompatibleThreadStateException {
    ThreadReference threadReference = getObjectReference().owningThread();
    return getVirtualMachineProxy().getThreadReferenceProxy(threadReference);
  }

  public int entryCount() throws IncompatibleThreadStateException {
    return getObjectReference().entryCount();
  }

  public boolean equals(Object o) {
    if (!(o instanceof ObjectReferenceProxyImpl)) {
      return false;
    }
    if(this == o) return true;

    ObjectReference ref = myObjectReference;
    return ref != null && ref.equals(((ObjectReferenceProxyImpl)o).myObjectReference);
  }


  public int hashCode() {
    return myObjectReference.hashCode();
  }

  /**
   * The advice to the proxy to clear cached data.
   */
  protected void clearCaches() {
    if (Boolean.FALSE.equals(myIsCollected)) {
      // clearing cache makes sence only if the object has not been collected yet
      myIsCollected = null;
    }
  }
}
