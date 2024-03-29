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
package com.intellij.java.debugger.impl.ui.impl.watch;

import jakarta.annotation.Nonnull;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.JavaValueModifier;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.PositionUtil;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.impl.ui.tree.FieldDescriptor;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import consulo.language.util.IncorrectOperationException;
import consulo.execution.debug.frame.XValueModifier;
import consulo.internal.com.sun.jdi.*;
import jakarta.annotation.Nullable;

public class FieldDescriptorImpl extends ValueDescriptorImpl implements FieldDescriptor
{
	public static final String OUTER_LOCAL_VAR_FIELD_PREFIX = "val$";
	private final Field myField;
	private final ObjectReference myObject;
	private Boolean myIsPrimitive = null;
	private final boolean myIsStatic;

	public FieldDescriptorImpl(Project project, ObjectReference objRef, @Nonnull Field field)
	{
		super(project);
		myObject = objRef;
		myField = field;
		myIsStatic = field.isStatic();
		setLvalue(!field.isFinal());
	}

	@Override
	public Field getField()
	{
		return myField;
	}

	@Override
	public ObjectReference getObject()
	{
		return myObject;
	}

	@Override
	public void setAncestor(NodeDescriptor oldDescriptor)
	{
		super.setAncestor(oldDescriptor);
		final Boolean isPrimitive = ((FieldDescriptorImpl) oldDescriptor).myIsPrimitive;
		if(isPrimitive != null)
		{ // was cached
			// do not loose cached info
			myIsPrimitive = isPrimitive;
		}
	}


	@Override
	public boolean isPrimitive()
	{
		if(myIsPrimitive == null)
		{
			final Value value = getValue();
			if(value != null)
			{
				myIsPrimitive = super.isPrimitive();
			}
			else
			{
				myIsPrimitive = DebuggerUtils.isPrimitiveType(myField.typeName());
			}
		}
		return myIsPrimitive.booleanValue();
	}

	@Override
	public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException
	{
		DebuggerManagerThreadImpl.assertIsManagerThread();
		try
		{
			return (myObject != null) ? myObject.getValue(myField) : myField.declaringType().getValue(myField);
		}
		catch(ObjectCollectedException ignored)
		{
			throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
		}
	}

	public boolean isStatic()
	{
		return myIsStatic;
	}

	@Override
	public String getName()
	{
		return myField.name();
	}

	@Override
	public String calcValueName()
	{
		String res = super.calcValueName();
		if(Boolean.TRUE.equals(getUserData(SHOW_DECLARING_TYPE)))
		{
			return NodeRendererSettings.getInstance().getClassRenderer().renderTypeName(myField.declaringType().name()) + "." + res;
		}
		return res;
	}

	public boolean isOuterLocalVariableValue()
	{
		try
		{
			return DebuggerUtils.isSynthetic(myField) && myField.name().startsWith(OUTER_LOCAL_VAR_FIELD_PREFIX);
		}
		catch(UnsupportedOperationException ignored)
		{
			return false;
		}
	}

	@Nullable
	@Override
	public String getDeclaredType()
	{
		return myField.typeName();
	}

	@Override
	public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException
	{
		PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
		String fieldName;
		if(isStatic())
		{
			String typeName = myField.declaringType().name().replace('$', '.');
			typeName = DebuggerTreeNodeExpression.normalize(typeName, PositionUtil.getContextElement(context), myProject);
			fieldName = typeName + "." + getName();
		}
		else
		{
			//noinspection HardCodedStringLiteral
			fieldName = isOuterLocalVariableValue() ? StringUtil.trimStart(getName(), OUTER_LOCAL_VAR_FIELD_PREFIX) : "this." + getName();
		}
		try
		{
			return elementFactory.createExpressionFromText(fieldName, null);
		}
		catch(IncorrectOperationException e)
		{
			throw new EvaluateException(DebuggerBundle.message("error.invalid.field.name", getName()), e);
		}
	}

	@Override
	public XValueModifier getModifier(JavaValue value)
	{
		return new JavaValueModifier(value)
		{
			@Override
			protected void setValueImpl(@Nonnull String expression, @Nonnull XModificationCallback callback)
			{
				final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();
				FieldDescriptorImpl fieldDescriptor = FieldDescriptorImpl.this;
				final Field field = fieldDescriptor.getField();
				if(!field.isStatic())
				{
					final ObjectReference object = fieldDescriptor.getObject();
					if(object != null)
					{
						set(expression, callback, debuggerContext, new SetValueRunnable()
						{
							public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException, InvalidTypeException, EvaluateException
							{
								object.setValue(field, preprocessValue(evaluationContext, newValue, field.type()));
								update(debuggerContext);
							}

							public ReferenceType loadClass(EvaluationContextImpl evaluationContext,
									String className) throws InvocationException, ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException, EvaluateException
							{
								return evaluationContext.getDebugProcess().loadClass(evaluationContext, className, field.declaringType().classLoader());
							}
						});
					}
				}
				else
				{
					// field is static
					ReferenceType refType = field.declaringType();
					if(refType instanceof ClassType)
					{
						final ClassType classType = (ClassType) refType;
						set(expression, callback, debuggerContext, new SetValueRunnable()
						{
							public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException, InvalidTypeException, EvaluateException
							{
								classType.setValue(field, preprocessValue(evaluationContext, newValue, field.type()));
								update(debuggerContext);
							}

							public ReferenceType loadClass(EvaluationContextImpl evaluationContext,
									String className) throws InvocationException, ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException, EvaluateException
							{
								return evaluationContext.getDebugProcess().loadClass(evaluationContext, className, field.declaringType().classLoader());
							}
						});
					}
				}
			}
		};
	}
}
