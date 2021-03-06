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
package com.intellij.debugger.memory.ui;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.swing.tree.TreePath;

import javax.annotation.Nullable;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorProvider;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.Value;

public class InstancesTree extends XDebuggerTree
{
	private final XValueNodeImpl myRoot;
	private final Runnable myOnRootExpandAction;
	private List<XValueChildrenList> myChildren;

	InstancesTree(@Nonnull Project project, @Nonnull XDebuggerEditorsProvider editorsProvider, @javax.annotation.Nullable XValueMarkers<?, ?> valueMarkers, @Nonnull Runnable onRootExpand)
	{
		super(project, editorsProvider, null, XDebuggerActions.INSPECT_TREE_POPUP_GROUP, valueMarkers);
		myOnRootExpandAction = onRootExpand;
		myRoot = new XValueNodeImpl(this, null, "root", new MyRootValue());

		myRoot.children();
		setRoot(myRoot, false);
		myRoot.setLeaf(false);
		setSelectionRow(0);
		expandNodesOnLoad(node -> node == myRoot);
	}

	void addChildren(@Nonnull XValueChildrenList children, boolean last)
	{
		if(myChildren == null)
		{
			myChildren = new ArrayList<>();
		}

		myChildren.add(children);
		myRoot.addChildren(children, last);
	}

	void rebuildTree(@Nonnull RebuildPolicy policy, @Nonnull XDebuggerTreeState state)
	{
		if(policy == RebuildPolicy.RELOAD_INSTANCES)
		{
			myChildren = null;
		}

		rebuildAndRestore(state);
	}

	void rebuildTree(@Nonnull RebuildPolicy policy)
	{
		rebuildTree(policy, XDebuggerTreeState.saveState(this));
	}

	void setInfoMessage(@SuppressWarnings("SameParameterValue") @Nonnull String text)
	{
		myChildren = null;
		myRoot.clearChildren();
		myRoot.setMessage(text, XDebuggerUIConstants.INFORMATION_MESSAGE_ICON, SimpleTextAttributes.REGULAR_ATTRIBUTES, null);
	}

	@Nullable
	ObjectReference getSelectedReference()
	{
		TreePath selectionPath = getSelectionPath();
		Object selectedItem = selectionPath != null ? selectionPath.getLastPathComponent() : null;
		if(selectedItem instanceof XValueNodeImpl)
		{
			XValueNodeImpl xValueNode = (XValueNodeImpl) selectedItem;
			XValue valueContainer = xValueNode.getValueContainer();

			if(valueContainer instanceof NodeDescriptorProvider)
			{
				NodeDescriptor descriptor = ((NodeDescriptorProvider) valueContainer).getDescriptor();

				if(descriptor instanceof ValueDescriptor)
				{
					Value value = ((ValueDescriptor) descriptor).getValue();

					if(value instanceof ObjectReference)
					{
						return (ObjectReference) value;
					}
				}
			}
		}

		return null;
	}

	enum RebuildPolicy
	{
		RELOAD_INSTANCES,
		ONLY_UPDATE_LABELS
	}

	private class MyRootValue extends XValue
	{
		@Override
		public void computeChildren(@Nonnull XCompositeNode node)
		{
			if(myChildren == null)
			{
				myOnRootExpandAction.run();
			}
			else
			{
				for(XValueChildrenList children : myChildren)
				{
					myRoot.addChildren(children, false);
				}

				myRoot.addChildren(XValueChildrenList.EMPTY, true);
			}
		}

		@Override
		public void computePresentation(@Nonnull XValueNode node, @Nonnull XValuePlace place)
		{
			node.setPresentation(null, "", "", true);
		}
	}
}
