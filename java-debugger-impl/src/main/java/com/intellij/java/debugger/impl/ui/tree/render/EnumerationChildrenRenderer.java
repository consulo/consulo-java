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

import java.util.ArrayList;
import java.util.List;

import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.debugger.impl.descriptors.data.UserExpressionData;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.java.debugger.impl.ui.tree.DebuggerTreeNode;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import com.intellij.java.debugger.impl.ui.tree.NodeDescriptorFactory;
import com.intellij.java.debugger.impl.ui.tree.NodeManager;
import com.intellij.java.debugger.impl.ui.tree.UserExpressionDescriptor;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizerUtil;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.language.psi.PsiElement;
import consulo.internal.com.sun.jdi.Value;

import jakarta.annotation.Nullable;

public final class EnumerationChildrenRenderer extends TypeRenderer implements ChildrenRenderer
{
	public static final
	@NonNls
	String UNIQUE_ID = "EnumerationChildrenRenderer";

	private boolean myAppendDefaultChildren;
	private List<ChildInfo> myChildren;

	public static final
	@NonNls
	String APPEND_DEFAULT_NAME = "AppendDefault";
	public static final
	@NonNls
	String CHILDREN_EXPRESSION = "ChildrenExpression";
	public static final
	@NonNls
	String CHILD_NAME = "Name";
	public static final
	@NonNls
	String CHILD_ONDEMAND = "OnDemand";

	public EnumerationChildrenRenderer()
	{
		this(new ArrayList<>());
	}

	public EnumerationChildrenRenderer(List<ChildInfo> children)
	{
		super();
		myChildren = children;
	}

	public void setAppendDefaultChildren(boolean appendDefaultChildren)
	{
		myAppendDefaultChildren = appendDefaultChildren;
	}

	public boolean isAppendDefaultChildren()
	{
		return myAppendDefaultChildren;
	}

	@Override
	public String getUniqueId()
	{
		return UNIQUE_ID;
	}

	@Override
	public EnumerationChildrenRenderer clone()
	{
		return (EnumerationChildrenRenderer) super.clone();
	}

	@Override
	public void readExternal(Element element) throws InvalidDataException
	{
		super.readExternal(element);

		myChildren.clear();

		myAppendDefaultChildren = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, APPEND_DEFAULT_NAME));

		List<Element> children = element.getChildren(CHILDREN_EXPRESSION);
		for(Element item : children)
		{
			String name = item.getAttributeValue(CHILD_NAME);
			TextWithImports text = DebuggerUtils.getInstance().readTextWithImports(item.getChildren().get(0));
			boolean onDemand = Boolean.parseBoolean(item.getAttributeValue(CHILD_ONDEMAND));

			myChildren.add(new ChildInfo(name, text, onDemand));
		}
	}

	@Override
	public void writeExternal(Element element) throws WriteExternalException
	{
		super.writeExternal(element);

		if(myAppendDefaultChildren)
		{
			JDOMExternalizerUtil.writeField(element, APPEND_DEFAULT_NAME, "true");
		}

		for(ChildInfo childInfo : myChildren)
		{
			Element child = new Element(CHILDREN_EXPRESSION);
			child.setAttribute(CHILD_NAME, childInfo.myName);
			if(childInfo.myOnDemand)
			{
				child.setAttribute(CHILD_ONDEMAND, "true");
			}
			child.addContent(DebuggerUtils.getInstance().writeTextWithImports(childInfo.myExpression));

			element.addContent(child);
		}
	}

	@Override
	public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext)
	{
		NodeManager nodeManager = builder.getNodeManager();
		NodeDescriptorFactory descriptorFactory = builder.getDescriptorManager();

		List<DebuggerTreeNode> children = new ArrayList<>();
		int idx = 0;
		for(ChildInfo childInfo : myChildren)
		{
			UserExpressionData data = new UserExpressionData((ValueDescriptorImpl) builder.getParentDescriptor(), getClassName(), childInfo.myName, childInfo.myExpression);
			data.setEnumerationIndex(idx++);
			UserExpressionDescriptor descriptor = descriptorFactory.getUserExpressionDescriptor(builder.getParentDescriptor(), data);
			if(childInfo.myOnDemand)
			{
				descriptor.putUserData(OnDemandRenderer.ON_DEMAND_CALCULATED, false);
			}
			children.add(nodeManager.createNode(descriptor, evaluationContext));
		}
		builder.addChildren(children, !myAppendDefaultChildren);

		if(myAppendDefaultChildren)
		{
			DebugProcessImpl.getDefaultRenderer(value).buildChildren(value, builder, evaluationContext);
		}
	}

	@Override
	public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException
	{
		return ((ValueDescriptor) node.getDescriptor()).getDescriptorEvaluation(context);
	}

	@Override
	public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor)
	{
		return myChildren.size() > 0 || (myAppendDefaultChildren && DebugProcessImpl.getDefaultRenderer(value).isExpandable(value, evaluationContext, parentDescriptor));
	}

	public List<ChildInfo> getChildren()
	{
		return myChildren;
	}

	public void setChildren(List<ChildInfo> children)
	{
		myChildren = children;
	}

	@Nullable
	public static EnumerationChildrenRenderer getCurrent(ValueDescriptorImpl valueDescriptor)
	{
		Renderer renderer = valueDescriptor.getLastRenderer();
		if(renderer instanceof CompoundNodeRenderer && NodeRendererSettings.getInstance().getCustomRenderers().contains((NodeRenderer) renderer))
		{
			ChildrenRenderer childrenRenderer = ((CompoundNodeRenderer) renderer).getChildrenRenderer();
			if(childrenRenderer instanceof EnumerationChildrenRenderer)
			{
				return (EnumerationChildrenRenderer) childrenRenderer;
			}
		}
		return null;
	}

	public static class ChildInfo implements Cloneable
	{
		public String myName;
		public TextWithImports myExpression;
		public boolean myOnDemand;

		public ChildInfo(String name, TextWithImports expression, boolean onDemand)
		{
			myName = name;
			myExpression = expression;
			myOnDemand = onDemand;
		}
	}
}
