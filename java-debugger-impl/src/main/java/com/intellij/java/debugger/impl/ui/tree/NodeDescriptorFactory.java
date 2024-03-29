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
package com.intellij.java.debugger.impl.ui.tree;

import com.intellij.java.debugger.engine.jdi.LocalVariableProxy;
import com.intellij.java.debugger.impl.descriptors.data.DescriptorData;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import consulo.internal.com.sun.jdi.ArrayReference;
import consulo.internal.com.sun.jdi.Field;
import consulo.internal.com.sun.jdi.ObjectReference;

/**
 * creates descriptors
 * if descriptor was already created in current context (that is location in debugee code) returns that descriptor
 * else creates new descriptor and restores it's representation properties from history
 */

public interface NodeDescriptorFactory {
  ArrayElementDescriptor getArrayItemDescriptor(NodeDescriptor parent, ArrayReference array, int index);

  FieldDescriptor getFieldDescriptor(NodeDescriptor parent, ObjectReference objRef, Field field);

  LocalVariableDescriptor getLocalVariableDescriptor(NodeDescriptor parent, LocalVariableProxy local);

  UserExpressionDescriptor getUserExpressionDescriptor(NodeDescriptor parent, final DescriptorData<UserExpressionDescriptor> data);
}
