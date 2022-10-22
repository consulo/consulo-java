/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.awt.event.MouseEvent;
import java.util.Collections;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import consulo.execution.debug.breakpoint.XExpression;
import consulo.execution.debug.frame.XDebuggerTreeNodeHyperlink;
import consulo.logging.Logger;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.actions.ArrayAction;
import com.intellij.java.debugger.impl.engine.ContextUtil;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.TextWithImportsImpl;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.memory.utils.ErrorsValueGroup;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.impl.settings.ViewsGeneralSettings;
import com.intellij.java.debugger.impl.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeManagerImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.java.debugger.impl.ui.tree.DebuggerTreeNode;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import com.intellij.java.debugger.impl.ui.tree.NodeDescriptorFactory;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import consulo.application.AllIcons;
import consulo.util.xml.serializer.DefaultJDOMExternalizer;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.language.util.IncorrectOperationException;
import consulo.execution.debug.frame.XCompositeNode;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.internal.com.sun.jdi.ArrayReference;
import consulo.internal.com.sun.jdi.ArrayType;
import consulo.internal.com.sun.jdi.Type;
import consulo.internal.com.sun.jdi.Value;

public class ArrayRenderer extends NodeRendererImpl
{
	private static final Logger LOG = Logger.getInstance(ArrayRenderer.class);

	@NonNls
	public static final String UNIQUE_ID = "ArrayRenderer";

	public int myStartIndex = 0;
	public int myEndIndex = Integer.MAX_VALUE;
	public int myEntriesLimit = XCompositeNode.MAX_CHILDREN_TO_SHOW;

	private boolean myForced = false;

	public ArrayRenderer()
	{
		myProperties.setEnabled(true);
	}

	@Override
	public String getUniqueId()
	{
		return UNIQUE_ID;
	}

	@Override
	public
	@NonNls
	String getName()
	{
		return "Array";
	}

	@Override
	public void setName(String text)
	{
		LOG.assertTrue(false);
	}

	@Override
	public ArrayRenderer clone()
	{
		return (ArrayRenderer) super.clone();
	}

	@Override
	public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) throws EvaluateException
	{
		return ClassRenderer.calcLabel(descriptor);
	}

	public void setForced(boolean forced)
	{
		myForced = forced;
	}

	@Override
	public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext)
	{
		DebuggerManagerThreadImpl.assertIsManagerThread();
		NodeManagerImpl nodeManager = (NodeManagerImpl) builder.getNodeManager();
		NodeDescriptorFactory descriptorFactory = builder.getDescriptorManager();

		ArrayReference array = (ArrayReference) value;
		int arrayLength = array.length();
		if(arrayLength > 0)
		{
			if(!myForced)
			{
				builder.initChildrenArrayRenderer(this, arrayLength);
			}

			if(myEntriesLimit <= 0)
			{
				myEntriesLimit = 1;
			}

			int added = 0;
			boolean hiddenNulls = false;
			int end = Math.min(arrayLength - 1, myEndIndex);
			int idx = myStartIndex;
			if(arrayLength > myStartIndex)
			{
				for(; idx <= end; idx++)
				{
					if(ViewsGeneralSettings.getInstance().HIDE_NULL_ARRAY_ELEMENTS && elementIsNull(array, idx))
					{
						hiddenNulls = true;
						continue;
					}

					DebuggerTreeNode arrayItemNode = nodeManager.createNode(descriptorFactory.getArrayItemDescriptor(builder.getParentDescriptor(), array, idx), evaluationContext);

					builder.addChildren(Collections.singletonList(arrayItemNode), false);
					added++;
					if(added >= myEntriesLimit)
					{
						break;
					}
				}
			}

			builder.addChildren(Collections.emptyList(), true);

			if(added == 0)
			{
				if(myStartIndex == 0 && arrayLength - 1 <= myEndIndex)
				{
					builder.setMessage(DebuggerBundle.message("message.node.all.elements.null"), null, SimpleTextAttributes.REGULAR_ATTRIBUTES, null);
				}
				else
				{
					builder.setMessage(DebuggerBundle.message("message.node.all.array.elements.null", myStartIndex, myEndIndex), null, SimpleTextAttributes.REGULAR_ATTRIBUTES, null);
				}
			}
			else
			{
				if(hiddenNulls)
				{
					builder.setMessage(DebuggerBundle.message("message.node.elements.null.hidden"), null, SimpleTextAttributes.REGULAR_ATTRIBUTES, null);
				}
				if(!myForced && idx < end)
				{
					builder.tooManyChildren(end - idx);
				}
			}
		}
	}

	private static boolean elementIsNull(ArrayReference arrayReference, int index)
	{
		try
		{
			return ArrayElementDescriptorImpl.getArrayElement(arrayReference, index) == null;
		}
		catch(EvaluateException e)
		{
			return false;
		}
	}

	@Override
	public void readExternal(Element element) throws InvalidDataException
	{
		super.readExternal(element);
		DefaultJDOMExternalizer.readExternal(this, element);
	}

	@Override
	public void writeExternal(Element element) throws WriteExternalException
	{
		super.writeExternal(element);
		DefaultJDOMExternalizer.writeExternal(this, element);
	}

	@Override
	public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context)
	{
		LOG.assertTrue(node.getDescriptor() instanceof ArrayElementDescriptorImpl, node.getDescriptor().getClass().getName());
		ArrayElementDescriptorImpl descriptor = (ArrayElementDescriptorImpl) node.getDescriptor();

		PsiElementFactory elementFactory = JavaPsiFacade.getInstance(node.getProject()).getElementFactory();
		try
		{
			return elementFactory.createExpressionFromText("this[" + descriptor.getIndex() + "]", elementFactory.getArrayClass(LanguageLevel.HIGHEST));
		}
		catch(IncorrectOperationException e)
		{
			LOG.error(e);
			return null;
		}
	}

	@Override
	public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor)
	{
		return value instanceof ArrayReference && ((ArrayReference) value).length() > 0;
	}

	@Override
	public boolean isApplicable(Type type)
	{
		return type instanceof ArrayType;
	}

	public static class Filtered extends ArrayRenderer
	{
		private final XExpression myExpression;

		public Filtered(XExpression expression)
		{
			myExpression = expression;
		}

		public XExpression getExpression()
		{
			return myExpression;
		}

		@Override
		public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext)
		{
			DebuggerManagerThreadImpl.assertIsManagerThread();
			NodeManagerImpl nodeManager = (NodeManagerImpl) builder.getNodeManager();
			NodeDescriptorFactory descriptorFactory = builder.getDescriptorManager();

			builder.setMessage(DebuggerBundle.message("message.node.filtered") + " " + myExpression.getExpression(), AllIcons.General.Filter, SimpleTextAttributes.REGULAR_ATTRIBUTES,
					FILTER_HYPERLINK);

			if(myEntriesLimit <= 0)
			{
				myEntriesLimit = 1;
			}

			ArrayReference array = (ArrayReference) value;
			int arrayLength = array.length();
			if(arrayLength > 0)
			{
				builder.initChildrenArrayRenderer(this, arrayLength);

				CachedEvaluator cachedEvaluator = new CachedEvaluator()
				{
					@Override
					protected String getClassName()
					{
						return ((ArrayType) array.type()).componentTypeName();
					}

					@Override
					protected PsiElement overrideContext(PsiElement context)
					{
						return ContextUtil.getContextElement(evaluationContext);
					}
				};
				cachedEvaluator.setReferenceExpression(TextWithImportsImpl.fromXExpression(myExpression));

				int added = 0;
				if(arrayLength - 1 >= myStartIndex)
				{
					ErrorsValueGroup errorsGroup = null;
					for(int idx = myStartIndex; idx < arrayLength; idx++)
					{
						try
						{
							if(DebuggerUtilsEx.evaluateBoolean(cachedEvaluator.getEvaluator(evaluationContext.getProject()), (EvaluationContextImpl) evaluationContext.createEvaluationContext(array
									.getValue(idx))))
							{

								DebuggerTreeNode arrayItemNode = nodeManager.createNode(descriptorFactory.getArrayItemDescriptor(builder.getParentDescriptor(), array, idx), evaluationContext);

								builder.addChildren(Collections.singletonList(arrayItemNode), false);
								added++;
								//if (added > ENTRIES_LIMIT) {
								//  break;
								//}
							}
						}
						catch(EvaluateException e)
						{
							if(errorsGroup == null)
							{
								errorsGroup = new ErrorsValueGroup();
								builder.addChildren(XValueChildrenList.bottomGroup(errorsGroup), false);
							}
							JavaValue childValue = JavaValue.create(null, (ValueDescriptorImpl) descriptorFactory.getArrayItemDescriptor(builder.getParentDescriptor(), array, idx), (
									(EvaluationContextImpl) evaluationContext), nodeManager, false);
							errorsGroup.addErrorValue(e.getMessage(), childValue);
						}
					}
				}

				builder.addChildren(Collections.emptyList(), true);

				//if (added != 0 && END_INDEX < arrayLength - 1) {
				//  builder.setRemaining(arrayLength - 1 - END_INDEX);
				//}
			}
		}

		public static final XDebuggerTreeNodeHyperlink FILTER_HYPERLINK = new XDebuggerTreeNodeHyperlink(" clear")
		{
			@Override
			public void onClick(MouseEvent e)
			{
				XDebuggerTree tree = (XDebuggerTree) e.getSource();
				TreePath path = tree.getPathForLocation(e.getX(), e.getY());
				if(path != null)
				{
					TreeNode parent = ((TreeNode) path.getLastPathComponent()).getParent();
					if(parent instanceof XValueNodeImpl)
					{
						consulo.ide.impl.idea.xdebugger.impl.ui.tree.nodes.XValueNodeImpl valueNode = (consulo.ide.impl.idea.xdebugger.impl.ui.tree.nodes.XValueNodeImpl) parent;
						ArrayAction.setArrayRenderer(NodeRendererSettings.getInstance().getArrayRenderer(), valueNode, DebuggerManagerEx.getInstanceEx(tree.getProject()).getContext());
					}
				}
				e.consume();
			}
		};
	}
}
