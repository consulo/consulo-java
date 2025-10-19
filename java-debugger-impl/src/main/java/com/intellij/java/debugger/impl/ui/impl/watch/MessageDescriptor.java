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
import com.intellij.java.debugger.impl.ui.tree.render.DescriptorLabelListener;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.execution.debug.localize.XDebuggerLocalize;
import consulo.localize.LocalizeValue;

public class MessageDescriptor extends NodeDescriptorImpl {
  public static final int ERROR = 0;
  public static final int WARNING = 1;
  public static final int INFORMATION = 2;
  public static final int SPECIAL = 3;
  private int myKind;
  private LocalizeValue myMessage;

  public static MessageDescriptor DEBUG_INFO_UNAVAILABLE = new MessageDescriptor(JavaDebuggerLocalize.messageNodeDebugInfoNotAvailable());
  public static MessageDescriptor LOCAL_VARIABLES_INFO_UNAVAILABLE = new MessageDescriptor(
      JavaDebuggerLocalize.messageNodeLocalVariablesDebugInfoNotAvailable()
  );
  public static MessageDescriptor ALL_ELEMENTS_IN_VISIBLE_RANGE_ARE_NULL = new MessageDescriptor(
      JavaDebuggerLocalize.messageNodeAllArrayElementsNull("", ""));
  public static MessageDescriptor ALL_ELEMENTS_IN_RANGE_ARE_NULL = new MessageDescriptor(
      JavaDebuggerLocalize.messageNodeAllElementsNull());
  public static MessageDescriptor ARRAY_IS_EMPTY = new MessageDescriptor(JavaDebuggerLocalize.messageNodeEmptyArray());
  public static MessageDescriptor CLASS_HAS_NO_FIELDS = new MessageDescriptor(JavaDebuggerLocalize.messageNodeClassHasNoFields());
  public static MessageDescriptor OBJECT_COLLECTED = new MessageDescriptor(JavaDebuggerLocalize.messageNodeObjectCollected());
  public static MessageDescriptor EVALUATING = new MessageDescriptor(XDebuggerLocalize.xdebuggerBuildingTreeNodeMessage());
  public static MessageDescriptor THREAD_IS_RUNNING = new MessageDescriptor(JavaDebuggerLocalize.messageNodeThreadRunning());
  public static MessageDescriptor THREAD_IS_EMPTY = new MessageDescriptor(JavaDebuggerLocalize.messageNodeThreadHasNoFrames());
  public static MessageDescriptor EVALUATION_NOT_POSSIBLE = new MessageDescriptor(JavaDebuggerLocalize.messageNodeEvaluationNotPossible(), WARNING);

  public MessageDescriptor(LocalizeValue message) {
    this(message, INFORMATION);
  }

  public MessageDescriptor(LocalizeValue message, int kind) {
    myKind = kind;
    myMessage = message;
  }

  public int getKind() {
    return myKind;
  }

  @Override
  public LocalizeValue getLabel() {
    return myMessage;
  }

  @Override
  public boolean isExpandable() {
    return false;
  }

  @Override
  public void setContext(EvaluationContextImpl context) {
  }

  @Override
  protected LocalizeValue calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener labelListener) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return myMessage;
  }
}