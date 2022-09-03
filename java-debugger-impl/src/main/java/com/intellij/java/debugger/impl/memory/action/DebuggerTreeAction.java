/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.memory.action;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeDescriptorProvider;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import consulo.execution.debug.frame.XValue;
import consulo.ide.impl.idea.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import consulo.ide.impl.idea.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.Value;

public abstract class DebuggerTreeAction extends XDebuggerTreeActionBase
{
	@Nullable
	protected ObjectReference getObjectReference(@Nonnull consulo.ide.impl.idea.xdebugger.impl.ui.tree.nodes.XValueNodeImpl node)
	{
		XValue valueContainer = node.getValueContainer();
		if(valueContainer instanceof NodeDescriptorProvider)
		{
			NodeDescriptor descriptor = ((NodeDescriptorProvider) valueContainer).getDescriptor();
			if(descriptor instanceof ValueDescriptor)
			{
				Value value = ((ValueDescriptor) descriptor).getValue();
				return value instanceof ObjectReference ? (ObjectReference) value : null;
			}
		}

		return null;
	}
}
