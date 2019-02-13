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
package com.intellij.debugger.actions;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.psi.PsiExpression;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodePresentationConfigurator;
import consulo.internal.com.sun.jdi.Field;
import consulo.internal.com.sun.jdi.ObjectCollectedException;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.Value;
import consulo.ui.image.Image;

public class JavaReferringObjectsValue extends JavaValue
{
	private static final long MAX_REFERRING = 100;
	private final boolean myIsField;

	private JavaReferringObjectsValue(@Nullable JavaValue parent,
			@Nonnull ValueDescriptorImpl valueDescriptor,
			@Nonnull EvaluationContextImpl evaluationContext,
			NodeManagerImpl nodeManager,
			boolean isField)
	{
		super(parent, valueDescriptor, evaluationContext, nodeManager, false);
		myIsField = isField;
	}

	public JavaReferringObjectsValue(@Nonnull JavaValue javaValue, boolean isField)
	{
		super(null, javaValue.getDescriptor(), javaValue.getEvaluationContext(), javaValue.getNodeManager(), false);
		myIsField = isField;
	}

	@Override
	public boolean canNavigateToSource()
	{
		return true;
	}

	@Override
	public void computeChildren(@Nonnull final XCompositeNode node)
	{
		scheduleCommand(getEvaluationContext(), node, new SuspendContextCommandImpl(getEvaluationContext().getSuspendContext())
		{
			@Override
			public Priority getPriority()
			{
				return Priority.NORMAL;
			}

			@Override
			public void contextAction(@Nonnull SuspendContextImpl suspendContext) throws Exception
			{
				final XValueChildrenList children = new XValueChildrenList();

				Value value = getDescriptor().getValue();

				List<ObjectReference> references;
				try
				{
					references = ((ObjectReference) value).referringObjects(MAX_REFERRING);
				}
				catch(ObjectCollectedException e)
				{
					node.setErrorMessage(DebuggerBundle.message("evaluation.error.object.collected"));
					return;
				}

				int i = 1;
				for(final ObjectReference reference : references)
				{
					// try to find field name
					Field field = findField(reference, value);
					if(field != null)
					{
						ValueDescriptorImpl descriptor = new FieldDescriptorImpl(getProject(), reference, field)
						{
							@Override
							public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException
							{
								return reference;
							}
						};
						children.add(new JavaReferringObjectsValue(null, descriptor, getEvaluationContext(), getNodeManager(), true));
						i++;
					}
					else
					{
						ValueDescriptorImpl descriptor = new ValueDescriptorImpl(getProject(), reference)
						{
							@Override
							public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException
							{
								return reference;
							}

							@Override
							public String getName()
							{
								return "Ref";
							}

							@Override
							public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException
							{
								return null;
							}
						};
						children.add("Referrer " + i++, new JavaReferringObjectsValue(null, descriptor, getEvaluationContext(), getNodeManager(), false));
					}
				}

				node.addChildren(children, true);
			}
		});
	}

	@Override
	public void computePresentation(@Nonnull final XValueNode node, @Nonnull final XValuePlace place)
	{
		if(!myIsField)
		{
			super.computePresentation(node, place);
		}
		else
		{
			super.computePresentation(new XValueNodePresentationConfigurator.ConfigurableXValueNodeImpl()
			{
				@Override
				public void applyPresentation(@Nullable Image icon, @Nonnull final XValuePresentation valuePresenter, boolean hasChildren)
				{
					node.setPresentation(icon, new XValuePresentation()
					{
						@Nonnull
						@Override
						public String getSeparator()
						{
							return " in ";
						}

						@javax.annotation.Nullable
						@Override
						public String getType()
						{
							return valuePresenter.getType();
						}

						@Override
						public void renderValue(@Nonnull XValueTextRenderer renderer)
						{
							valuePresenter.renderValue(renderer);
						}
					}, hasChildren);
				}

				@Override
				public void setFullValueEvaluator(@Nonnull XFullValueEvaluator fullValueEvaluator)
				{
				}

				@Override
				public boolean isObsolete()
				{
					return false;
				}
			}, place);
		}
	}

	private static Field findField(ObjectReference reference, Value value)
	{
		for(Field field : reference.referenceType().allFields())
		{
			if(reference.getValue(field) == value)
			{
				return field;
			}
		}
		return null;
	}

	@javax.annotation.Nullable
	@Override
	public XValueModifier getModifier()
	{
		return null;
	}
}
