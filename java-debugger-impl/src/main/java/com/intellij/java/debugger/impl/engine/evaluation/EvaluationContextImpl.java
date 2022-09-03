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
package com.intellij.java.debugger.impl.engine.evaluation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import consulo.project.Project;
import consulo.ide.impl.idea.openapi.util.NullableLazyValue;
import consulo.internal.com.sun.jdi.ClassLoaderReference;
import consulo.internal.com.sun.jdi.Value;

public final class EvaluationContextImpl implements EvaluationContext
{
	private final NullableLazyValue<Value> myThisObject;
	private final SuspendContextImpl mySuspendContext;
	private final StackFrameProxyImpl myFrameProxy;
	private boolean myAutoLoadClasses = true;
	private ClassLoaderReference myClassLoader;

	public EvaluationContextImpl(@Nonnull SuspendContextImpl suspendContext, StackFrameProxyImpl frameProxy, @Nullable Value thisObject)
	{
		myThisObject = NullableLazyValue.of(() -> thisObject);
		myFrameProxy = frameProxy;
		mySuspendContext = suspendContext;
	}

	public EvaluationContextImpl(@Nonnull SuspendContextImpl suspendContext, @Nonnull StackFrameProxyImpl frameProxy)
	{
		myThisObject = NullableLazyValue.of(() ->
		{
			try
			{
				return frameProxy.thisObject();
			}
			catch(EvaluateException ignore)
			{
			}
			return null;
		});
		myFrameProxy = frameProxy;
		mySuspendContext = suspendContext;
	}

	@Nullable
	@Override
	public Value getThisObject()
	{
		return myThisObject.getValue();
	}

	@Nonnull
	@Override
	public SuspendContextImpl getSuspendContext()
	{
		return mySuspendContext;
	}

	@Override
	public StackFrameProxyImpl getFrameProxy()
	{
		return myFrameProxy;
	}

	@Nonnull
	@Override
	public DebugProcessImpl getDebugProcess()
	{
		return getSuspendContext().getDebugProcess();
	}

	public DebuggerManagerThreadImpl getManagerThread()
	{
		return getDebugProcess().getManagerThread();
	}

	@Override
	public Project getProject()
	{
		DebugProcessImpl debugProcess = getDebugProcess();
		return debugProcess.getProject();
	}

	@Override
	public EvaluationContextImpl createEvaluationContext(Value value)
	{
		final EvaluationContextImpl copy = new EvaluationContextImpl(getSuspendContext(), getFrameProxy(), value);
		copy.setAutoLoadClasses(myAutoLoadClasses);
		return copy;
	}

	@Nullable
	@Override
	public ClassLoaderReference getClassLoader() throws EvaluateException
	{
		DebuggerManagerThreadImpl.assertIsManagerThread();
		if(myClassLoader != null)
		{
			return myClassLoader;
		}
		return myFrameProxy != null ? myFrameProxy.getClassLoader() : null;
	}

	public void setClassLoader(ClassLoaderReference classLoader)
	{
		myClassLoader = classLoader;
	}

	public boolean isAutoLoadClasses()
	{
		return myAutoLoadClasses;
	}

	public void setAutoLoadClasses(final boolean autoLoadClasses)
	{
		myAutoLoadClasses = autoLoadClasses;
	}
}
