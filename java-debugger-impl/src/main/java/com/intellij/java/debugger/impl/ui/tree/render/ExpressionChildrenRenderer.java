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

import jakarta.annotation.Nullable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.java.debugger.impl.ui.tree.DebuggerTreeNode;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import com.intellij.java.debugger.impl.ui.tree.NodeManager;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import consulo.util.xml.serializer.DefaultJDOMExternalizer;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.dataholder.Key;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.util.lang.StringUtil;
import com.intellij.java.language.psi.PsiExpression;
import consulo.internal.com.sun.jdi.BooleanValue;
import consulo.internal.com.sun.jdi.Value;

public class ExpressionChildrenRenderer extends TypeRenderer implements ChildrenRenderer
{
	public static final
	@NonNls
	String UNIQUE_ID = "ExpressionChildrenRenderer";
	private static final Key<Value> EXPRESSION_VALUE = new Key<>("EXPRESSION_VALUE");
	private static final Key<NodeRenderer> LAST_CHILDREN_RENDERER = new Key<>("LAST_CHILDREN_RENDERER");

	private CachedEvaluator myChildrenExpandable = createCachedEvaluator();
	private CachedEvaluator myChildrenExpression = createCachedEvaluator();

	private NodeRenderer myPredictedRenderer;

	public String getUniqueId()
	{
		return UNIQUE_ID;
	}

	public ExpressionChildrenRenderer clone()
	{
		ExpressionChildrenRenderer clone = (ExpressionChildrenRenderer) super.clone();
		clone.myChildrenExpandable = createCachedEvaluator();
		clone.setChildrenExpandable(getChildrenExpandable());
		clone.myChildrenExpression = createCachedEvaluator();
		clone.setChildrenExpression(getChildrenExpression());
		return clone;
	}

	public void buildChildren(final Value value, final ChildrenBuilder builder, final EvaluationContext evaluationContext)
	{
		final NodeManager nodeManager = builder.getNodeManager();

		try
		{
			final ValueDescriptor parentDescriptor = builder.getParentDescriptor();
			final Value childrenValue = evaluateChildren(evaluationContext.createEvaluationContext(value), parentDescriptor);

			NodeRenderer renderer = getChildrenRenderer(childrenValue, parentDescriptor);
			renderer.buildChildren(childrenValue, builder, evaluationContext);
		}
		catch(final EvaluateException e)
		{
			List<DebuggerTreeNode> errorChildren = new ArrayList<>();
			errorChildren.add(nodeManager.createMessageNode(DebuggerBundle.message("error.unable.to.evaluate.expression") + " " + e.getMessage()));
			builder.setChildren(errorChildren);
		}
	}

	@Nullable
	public static NodeRenderer getLastChildrenRenderer(ValueDescriptor descriptor)
	{
		return descriptor.getUserData(LAST_CHILDREN_RENDERER);
	}

	public static void setPreferableChildrenRenderer(ValueDescriptor descriptor, NodeRenderer renderer)
	{
		descriptor.putUserData(LAST_CHILDREN_RENDERER, renderer);
	}

	public static Value getLastChildrenValue(NodeDescriptor descriptor)
	{
		return descriptor.getUserData(EXPRESSION_VALUE);
	}

	private Value evaluateChildren(EvaluationContext context, NodeDescriptor descriptor) throws EvaluateException
	{
		final ExpressionEvaluator evaluator = myChildrenExpression.getEvaluator(context.getProject());

		Value value = evaluator.evaluate(context);
		DebuggerUtilsEx.keep(value, context);

		descriptor.putUserData(EXPRESSION_VALUE, value);
		return value;
	}

	public void readExternal(Element element) throws InvalidDataException
	{
		super.readExternal(element);
		DefaultJDOMExternalizer.readExternal(this, element);

		TextWithImports childrenExpression = DebuggerUtils.getInstance().readTextWithImports(element, "CHILDREN_EXPRESSION");
		if(childrenExpression != null)
		{
			setChildrenExpression(childrenExpression);
		}

		TextWithImports childrenExpandable = DebuggerUtils.getInstance().readTextWithImports(element, "CHILDREN_EXPANDABLE");
		if(childrenExpandable != null)
		{
			myChildrenExpandable.setReferenceExpression(childrenExpandable);
		}
	}

	public void writeExternal(Element element) throws WriteExternalException
	{
		super.writeExternal(element);
		DefaultJDOMExternalizer.writeExternal(this, element);
		DebuggerUtils.getInstance().writeTextWithImports(element, "CHILDREN_EXPANDABLE", getChildrenExpandable());
		DebuggerUtils.getInstance().writeTextWithImports(element, "CHILDREN_EXPRESSION", getChildrenExpression());
	}

	public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException
	{
		Value expressionValue = getLastChildrenValue(node.getParent().getDescriptor());
		if(expressionValue == null)
		{
			throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("error.unable.to.evaluate.expression"));
		}

		NodeRenderer childrenRenderer = getChildrenRenderer(expressionValue, (ValueDescriptor) node.getParent().getDescriptor());

		PsiExpression childrenPsiExpression = myChildrenExpression.getPsiExpression(node.getProject());
		if(childrenPsiExpression == null)
		{
			return null;
		}
		return DebuggerTreeNodeExpression.substituteThis(childrenRenderer.getChildValueExpression(node, context), (PsiExpression) childrenPsiExpression.copy(), expressionValue);
	}

	private static NodeRenderer getChildrenRenderer(Value childrenValue, ValueDescriptor parentDescriptor)
	{
		NodeRenderer renderer = getLastChildrenRenderer(parentDescriptor);
		if(renderer == null || childrenValue == null || !renderer.isApplicable(childrenValue.type()))
		{
			renderer = DebugProcessImpl.getDefaultRenderer(childrenValue != null ? childrenValue.type() : null);
			setPreferableChildrenRenderer(parentDescriptor, renderer);
		}
		return renderer;
	}

	public boolean isExpandable(Value value, final EvaluationContext context, NodeDescriptor parentDescriptor)
	{
		final EvaluationContext evaluationContext = context.createEvaluationContext(value);

		if(!StringUtil.isEmpty(myChildrenExpandable.getReferenceExpression().getText()))
		{
			try
			{
				Value expanded = myChildrenExpandable.getEvaluator(evaluationContext.getProject()).evaluate(evaluationContext);
				if(expanded instanceof BooleanValue)
				{
					return ((BooleanValue) expanded).booleanValue();
				}
			}
			catch(EvaluateException e)
			{
				// ignored
			}
		}

		try
		{
			Value children = evaluateChildren(evaluationContext, parentDescriptor);
			ChildrenRenderer defaultChildrenRenderer = DebugProcessImpl.getDefaultRenderer(value.type());
			return defaultChildrenRenderer.isExpandable(children, evaluationContext, parentDescriptor);
		}
		catch(EvaluateException e)
		{
			return true;
		}
	}

	public TextWithImports getChildrenExpression()
	{
		return myChildrenExpression.getReferenceExpression();
	}

	public void setChildrenExpression(TextWithImports expression)
	{
		myChildrenExpression.setReferenceExpression(expression);
	}

	public TextWithImports getChildrenExpandable()
	{
		return myChildrenExpandable.getReferenceExpression();
	}

	public void setChildrenExpandable(TextWithImports childrenExpandable)
	{
		myChildrenExpandable.setReferenceExpression(childrenExpandable);
	}

	public void setClassName(String name)
	{
		super.setClassName(name);
		myChildrenExpression.clear();
		myChildrenExpandable.clear();
	}

	public NodeRenderer getPredictedRenderer()
	{
		return myPredictedRenderer;
	}

	public void setPredictedRenderer(NodeRenderer predictedRenderer)
	{
		myPredictedRenderer = predictedRenderer;
	}
}
