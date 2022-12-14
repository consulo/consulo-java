/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.ui.tree.render;

import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.ui.tree.DebuggerTreeNode;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import com.intellij.java.language.psi.PsiExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.internal.com.sun.jdi.LongType;
import consulo.internal.com.sun.jdi.LongValue;
import consulo.internal.com.sun.jdi.Type;
import consulo.internal.com.sun.jdi.Value;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @author egor
 */
@ExtensionImpl
public class TimestampRenderer extends NodeRendererImpl {
  @Override
  public String calcLabel(ValueDescriptor descriptor,
                          EvaluationContext evaluationContext,
                          DescriptorLabelListener listener) throws EvaluateException {
    Value value = descriptor.getValue();
    if (value == null) {
      return "null";
    }
    else if (value instanceof LongValue) {
      long time = ((LongValue)value).longValue();
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault()).toString();
    }
    return null;
  }

  @Override
  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
  }

  @Override
  public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    return null;
  }

  @Override
  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    return false;
  }

  @Override
  public String getName() {
    return "Timestamp";
  }

  @Override
  public String getUniqueId() {
    return "TimestampRenderer";
  }

  @Override
  public boolean isApplicable(Type t) {
    return t instanceof LongType;
  }
}
