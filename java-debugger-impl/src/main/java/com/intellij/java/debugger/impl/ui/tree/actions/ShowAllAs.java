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
package com.intellij.java.debugger.impl.ui.tree.actions;

import java.util.Enumeration;

import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.SuspendContext;
import com.intellij.java.debugger.engine.managerThread.SuspendContextCommand;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.tree.DebuggerTreeNode;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import com.intellij.java.debugger.impl.ui.tree.render.NodeRenderer;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.internal.com.sun.jdi.ArrayReference;
import consulo.internal.com.sun.jdi.ArrayType;
import consulo.internal.com.sun.jdi.ByteType;
import consulo.internal.com.sun.jdi.ClassNotLoadedException;
import consulo.internal.com.sun.jdi.IntegerType;
import consulo.internal.com.sun.jdi.LongType;
import consulo.internal.com.sun.jdi.PrimitiveType;
import consulo.internal.com.sun.jdi.ShortType;
import consulo.internal.com.sun.jdi.Type;

public class ShowAllAs extends AnAction
{
	private final NodeRenderer myRenderer;

	public ShowAllAs(NodeRenderer renderer)
	{
		myRenderer = renderer;
	}

	private boolean isPrimitiveArray(DebuggerTreeNode selectedNode)
	{
		try
		{
			if(selectedNode.getDescriptor() instanceof ValueDescriptor)
			{
				ValueDescriptor valueDescriptor = ((ValueDescriptor) selectedNode.getDescriptor());
				if(valueDescriptor.isArray())
				{
					ArrayReference arrayReference = ((ArrayReference) valueDescriptor.getValue());
					Type componentType = ((ArrayType) arrayReference.type()).componentType();
					if(componentType instanceof PrimitiveType)
					{
						if(componentType instanceof ByteType ||
								componentType instanceof ShortType ||
								componentType instanceof IntegerType ||
								componentType instanceof LongType)
						{
							return true;
						}
					}
				}
			}
		}
		catch(ClassNotLoadedException e)
		{
		}
		return false;
	}

	public void update(AnActionEvent e)
	{
		DebuggerTreeNode selectedNode = ((DebuggerUtilsEx) DebuggerUtils.getInstance()).getSelectedNode(e.getDataContext());
		e.getPresentation().setVisible(myRenderer != null && selectedNode != null && isPrimitiveArray(selectedNode));
	}

	public void actionPerformed(AnActionEvent e)
	{
		DebuggerTreeNodeImpl selectedNode = (DebuggerTreeNodeImpl) ((DebuggerUtilsEx) DebuggerUtils.getInstance()).getSelectedNode(e.getDataContext
				());
		if(selectedNode == null)
		{
			return;
		}

		if(!isPrimitiveArray(selectedNode))
		{
			return;
		}

		final DebuggerContext debuggerContext = DebuggerUtils.getInstance().getDebuggerContext(e.getDataContext());
		if(debuggerContext == null || debuggerContext.getDebugProcess() == null)
		{
			return;
		}

		for(Enumeration children = selectedNode.children(); children.hasMoreElements(); )
		{
			final DebuggerTreeNode child = (DebuggerTreeNode) children.nextElement();
			if(child.getDescriptor() instanceof ValueDescriptor)
			{
				debuggerContext.getDebugProcess().getManagerThread().invokeCommand(new SuspendContextCommand()
				{
					public SuspendContext getSuspendContext()
					{
						return debuggerContext.getSuspendContext();
					}

					public void action()
					{
						child.setRenderer(myRenderer);
					}

					public void commandCancelled()
					{
					}
				});
			}
		}
	}
}
