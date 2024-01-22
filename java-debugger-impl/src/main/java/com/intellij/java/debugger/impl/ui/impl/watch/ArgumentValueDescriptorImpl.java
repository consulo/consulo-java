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
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.JavaValueModifier;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.PositionUtil;
import com.intellij.java.debugger.impl.jdi.DecompiledLocalVariable;
import com.intellij.java.debugger.impl.jdi.LocalVariablesUtil;
import consulo.execution.debug.frame.XValueModifier;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import consulo.language.util.IncorrectOperationException;
import consulo.internal.com.sun.jdi.ClassNotLoadedException;
import consulo.internal.com.sun.jdi.IncompatibleThreadStateException;
import consulo.internal.com.sun.jdi.InvalidTypeException;
import consulo.internal.com.sun.jdi.InvocationException;
import consulo.internal.com.sun.jdi.PrimitiveValue;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.Value;

public class ArgumentValueDescriptorImpl extends ValueDescriptorImpl
{
	private final DecompiledLocalVariable myVariable;

	public ArgumentValueDescriptorImpl(Project project, DecompiledLocalVariable variable, Value value)
	{
		super(project, value);
		myVariable = variable;
		setLvalue(true);
	}

	@Override
	public boolean canSetValue()
	{
		return LocalVariablesUtil.canSetValues();
	}

	@Override
	public boolean isPrimitive()
	{
		return getValue() instanceof PrimitiveValue;
	}

	@Override
	public Value calcValue(final EvaluationContextImpl evaluationContext) throws EvaluateException
	{
		return getValue();
	}

	public DecompiledLocalVariable getVariable()
	{
		return myVariable;
	}

	@Override
	public String getName()
	{
		return myVariable.getDisplayName();
	}

	public boolean isParameter()
	{
		return myVariable.isParam();
	}

	@Override
	public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException
	{
		PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
		try
		{
			return elementFactory.createExpressionFromText(getName(), PositionUtil.getContextElement(context));
		}
		catch(IncorrectOperationException e)
		{
			throw new EvaluateException(DebuggerBundle.message("error.invalid.local.variable.name", getName()), e);
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
				final DecompiledLocalVariable local = ArgumentValueDescriptorImpl.this.getVariable();
				if(local != null)
				{
					final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();
					set(expression, callback, debuggerContext, new SetValueRunnable()
					{
						@Override
						public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException, InvalidTypeException, EvaluateException
						{
							LocalVariablesUtil.setValue(debuggerContext.getFrameProxy().getStackFrame(), local.getSlot(), newValue);
							update(debuggerContext);
						}

						@Override
						public ReferenceType loadClass(EvaluationContextImpl evaluationContext,
								String className) throws InvocationException, ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException, EvaluateException
						{
							return evaluationContext.getDebugProcess().loadClass(evaluationContext, className, evaluationContext.getClassLoader());
						}
					});
				}
			}
		};
	}
}