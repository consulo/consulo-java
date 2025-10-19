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

import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.ui.tree.render.DescriptorLabelListener;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import consulo.execution.debug.ui.ValueMarkup;
import consulo.internal.com.sun.jdi.InconsistentDebugInfoException;
import consulo.internal.com.sun.jdi.InvalidStackFrameException;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class NodeDescriptorImpl implements NodeDescriptor {
  protected static final Logger LOG = Logger.getInstance(NodeDescriptorImpl.class);

  public static final LocalizeValue UNKNOWN_VALUE_MESSAGE = LocalizeValue.empty();
  public boolean myIsExpanded = false;
  public boolean myIsSelected = false;
  public boolean myIsVisible  = false;
  public boolean myIsSynthetic = false;

  private EvaluateException myEvaluateException;
  private LocalizeValue myLabel = UNKNOWN_VALUE_MESSAGE;

  private HashMap<Key, Object> myUserData;

  private final List<NodeDescriptorImpl> myChildren = new ArrayList<NodeDescriptorImpl>();
  private static final Key<Map<ObjectReference, ValueMarkup>> MARKUP_MAP_KEY = new Key<Map<ObjectReference, ValueMarkup>>("ValueMarkupMap");

  @Override
  public String getName() {
    return null;
  }

  @Override
  public <T> T getUserData(Key<T> key) {
    if(myUserData == null) return null;
    return (T) myUserData.get(key);
  }

  @Override
  public <T> void putUserData(Key<T> key, T value) {
    if(myUserData == null) {
      myUserData = new HashMap<Key, Object>();
    }
    myUserData.put(key, value);
  }

  public void updateRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener){
    updateRepresentationNoNotify(context, labelListener);
    labelListener.labelChanged();
  }

  protected void updateRepresentationNoNotify(EvaluationContextImpl context, DescriptorLabelListener labelListener) {
    try {
      try {
        myEvaluateException = null;
        myLabel = calcRepresentation(context, labelListener);
      }
      catch (RuntimeException e) {
        LOG.debug(e);
        throw processException(e);
      }
    }
    catch (EvaluateException e) {
      setFailed(e);
    }
  }

  protected abstract LocalizeValue calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) throws EvaluateException;

  private EvaluateException processException(Exception e) {
    if(e instanceof InconsistentDebugInfoException) {
      return new EvaluateException(JavaDebuggerLocalize.errorInconsistentDebugInfo().get(), null);
    }

    else if(e instanceof InvalidStackFrameException) {
      return new EvaluateException(JavaDebuggerLocalize.errorInvalidStackframe().get(), null);
    }
    else {
      return EvaluateExceptionUtil.DEBUG_INFO_UNAVAILABLE;
    }
  }

  @Override
  public void displayAs(NodeDescriptor descriptor) {
    if (descriptor instanceof NodeDescriptorImpl) {
      final NodeDescriptorImpl that = (NodeDescriptorImpl)descriptor;
      myIsExpanded = that.myIsExpanded;
      myIsSelected = that.myIsSelected;
      myIsVisible  = that.myIsVisible;
      myUserData = that.myUserData != null ? new HashMap<Key, Object>(that.myUserData) : null;
    }
  }

  public abstract boolean isExpandable();

  public abstract void setContext(EvaluationContextImpl context);

  public EvaluateException getEvaluateException() {
    return myEvaluateException;
  }

  @Override
  public LocalizeValue getLabel() {
    return myLabel;
  }

  @Override
  public String toString() {
    return getLabel().get();
  }

  protected String setFailed(EvaluateException e) {
    myEvaluateException = e;
    return e.getMessage();
  }

  protected LocalizeValue setLabel(LocalizeValue customLabel) {
    return myLabel = customLabel;
  }

  //Context is set to null
  public void clear() {
    myEvaluateException = null;
    myLabel = LocalizeValue.empty();
  }

  public List<NodeDescriptorImpl> getChildren() {
    return myChildren;
  }

  @Override
  public void setAncestor(NodeDescriptor oldDescriptor) {
    displayAs(oldDescriptor);
  }

  @Nullable
  public static Map<ObjectReference, ValueMarkup> getMarkupMap(final DebugProcess process) {
    if (process == null) {
      return null;
    }
    Map<ObjectReference, ValueMarkup> map = process.getUserData(MARKUP_MAP_KEY);
    if (map == null) {
      map = new HashMap<ObjectReference, ValueMarkup>();
      process.putUserData(MARKUP_MAP_KEY, map);
    }
    return map;
  }
}
