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

import javax.annotation.Nonnull;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.JavaValueModifier;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.jdi.VirtualMachineProxyImpl;
import com.intellij.java.debugger.impl.ui.tree.ArrayElementDescriptor;
import consulo.execution.debug.frame.XValueModifier;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import consulo.language.util.IncorrectOperationException;
import consulo.internal.com.sun.jdi.ArrayReference;
import consulo.internal.com.sun.jdi.ArrayType;
import consulo.internal.com.sun.jdi.ClassNotLoadedException;
import consulo.internal.com.sun.jdi.IncompatibleThreadStateException;
import consulo.internal.com.sun.jdi.InvalidTypeException;
import consulo.internal.com.sun.jdi.InvocationException;
import consulo.internal.com.sun.jdi.ObjectCollectedException;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.Value;

public class ArrayElementDescriptorImpl extends ValueDescriptorImpl implements ArrayElementDescriptor
{
	private final int myIndex;
	private final ArrayReference myArray;

	public ArrayElementDescriptorImpl(Project project, ArrayReference array, int index)
	{
		super(project);
		myArray = array;
		myIndex = index;
		setLvalue(true);
	}

	@Override
	public int getIndex()
	{
		return myIndex;
	}

	@Override
	public ArrayReference getArray()
	{
		return myArray;
	}

	@Override
	public String getName()
	{
		return String.valueOf(myIndex);
	}

	@Override
	public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException
	{
		return getArrayElement(myArray, myIndex);
	}

	public static Value getArrayElement(ArrayReference reference, int idx) throws EvaluateException
	{
		try
		{
			return reference.getValue(idx);
		}
		catch(ObjectCollectedException e)
		{
			throw EvaluateExceptionUtil.ARRAY_WAS_COLLECTED;
		}
	}

	@Override
	public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException
	{
		PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
		try
		{
			return elementFactory.createExpressionFromText("this[" + myIndex + "]", null);
		}
		catch(IncorrectOperationException e)
		{
			throw new EvaluateException(e.getMessage(), e);
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
				final ArrayElementDescriptorImpl elementDescriptor = ArrayElementDescriptorImpl.this;
				final ArrayReference array = elementDescriptor.getArray();
				if(array != null)
				{
					if(VirtualMachineProxyImpl.isCollected(array))
					{
						// will only be the case if debugger does not use ObjectReference.disableCollection() because of Patches.IBM_JDK_DISABLE_COLLECTION_BUG
						Messages.showWarningDialog(getProject(), DebuggerBundle.message("evaluation.error.array.collected") + "\n" + DebuggerBundle.message("warning.recalculate"), DebuggerBundle
								.message("title.set.value"));
						//node.getParent().calcValue();
						return;
					}
					final ArrayType arrType = (ArrayType) array.referenceType();
					final DebuggerContextImpl debuggerContext = DebuggerManagerEx.getInstanceEx(getProject()).getContext();
					set(expression, callback, debuggerContext, new SetValueRunnable()
					{
						@Override
						public void setValue(EvaluationContextImpl evaluationContext, Value newValue) throws ClassNotLoadedException, InvalidTypeException, EvaluateException
						{
							array.setValue(elementDescriptor.getIndex(), preprocessValue(evaluationContext, newValue, arrType.componentType()));
							update(debuggerContext);
						}

						@Override
						public ReferenceType loadClass(EvaluationContextImpl evaluationContext,
								String className) throws InvocationException, ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException, EvaluateException
						{
							return evaluationContext.getDebugProcess().loadClass(evaluationContext, className, arrType.classLoader());
						}
					});
				}
			}
		};
	}
}
