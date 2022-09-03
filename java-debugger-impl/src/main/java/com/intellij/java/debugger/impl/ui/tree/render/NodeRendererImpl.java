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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jdom.Element;
import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.java.debugger.impl.ui.tree.DebuggerTreeNode;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import consulo.logging.Logger;
import consulo.language.psi.PsiElement;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.Value;
import consulo.ui.image.Image;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 9, 2005
 */
public abstract class NodeRendererImpl implements NodeRenderer
{
	private static final Logger LOG = Logger.getInstance(NodeRendererImpl.class);
	protected BasicRendererProperties myProperties;

	protected NodeRendererImpl()
	{
		this("unnamed");
	}

	protected NodeRendererImpl(@Nonnull String presentableName)
	{
		this(presentableName, false);
	}

	protected NodeRendererImpl(@Nonnull String presentableName, boolean enabledDefaultValue)
	{
		myProperties = new BasicRendererProperties(enabledDefaultValue);
		myProperties.setName(presentableName);
		myProperties.setEnabled(enabledDefaultValue);
	}

	@Override
	public String getName()
	{
		return myProperties.getName();
	}

	@Override
	public void setName(String name)
	{
		myProperties.setName(name);
	}

	@Override
	public boolean isEnabled()
	{
		return myProperties.isEnabled();
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		myProperties.setEnabled(enabled);
	}

	public boolean isShowType()
	{
		return myProperties.isShowType();
	}

	public void setShowType(boolean showType)
	{
		myProperties.setShowType(showType);
	}

	@Override
	public Image calcValueIcon(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) throws EvaluateException
	{
		return null;
	}

	@Override
	public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext)
	{
	}

	@Override
	public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException
	{
		return null;
	}

	@Override
	public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor)
	{
		return false;
	}

	@Override
	public NodeRendererImpl clone()
	{
		try
		{
			final NodeRendererImpl cloned = (NodeRendererImpl) super.clone();
			cloned.myProperties = myProperties.clone();
			return cloned;
		}
		catch(CloneNotSupportedException e)
		{
			LOG.error(e);
		}
		return null;
	}

	@Override
	public void readExternal(Element element)
	{
		myProperties.readExternal(element);
	}

	@Override
	public void writeExternal(Element element)
	{
		myProperties.writeExternal(element);
	}

	public String toString()
	{
		return getName();
	}

	@Nullable
	public String getIdLabel(Value value, DebugProcess process)
	{
		return value instanceof ObjectReference && isShowType() ? ValueDescriptorImpl.getIdLabel((ObjectReference) value) : null;
	}
}
