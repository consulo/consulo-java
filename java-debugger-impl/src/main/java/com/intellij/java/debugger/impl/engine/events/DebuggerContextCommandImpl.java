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
package com.intellij.java.debugger.impl.engine.events;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.engine.SuspendManager;
import com.intellij.java.debugger.impl.engine.SuspendManagerUtil;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import consulo.logging.Logger;
import consulo.internal.com.sun.jdi.ObjectCollectedException;

public abstract class DebuggerContextCommandImpl extends SuspendContextCommandImpl
{
	private static final Logger LOG = Logger.getInstance(DebuggerContextCommandImpl.class);

	private final DebuggerContextImpl myDebuggerContext;
	private final ThreadReferenceProxyImpl myCustomThread; // thread to perform command in
	private SuspendContextImpl myCustomSuspendContext;

	protected DebuggerContextCommandImpl(@Nonnull DebuggerContextImpl debuggerContext)
	{
		this(debuggerContext, null);
	}

	protected DebuggerContextCommandImpl(@Nonnull DebuggerContextImpl debuggerContext, @Nullable ThreadReferenceProxyImpl customThread)
	{
		super(debuggerContext.getSuspendContext());
		myDebuggerContext = debuggerContext;
		myCustomThread = customThread;
	}

	@Nullable
	@Override
	public SuspendContextImpl getSuspendContext()
	{
		if(myCustomSuspendContext == null)
		{
			myCustomSuspendContext = super.getSuspendContext();
			ThreadReferenceProxyImpl thread = getThread();
			if(myCustomThread != null && (myCustomSuspendContext == null || myCustomSuspendContext.isResumed() || !myCustomSuspendContext.suspends(thread)))
			{
				myCustomSuspendContext = SuspendManagerUtil.findContextByThread(myDebuggerContext.getDebugProcess().getSuspendManager(), thread);
			}
		}
		return myCustomSuspendContext;
	}

	private ThreadReferenceProxyImpl getThread()
	{
		return myCustomThread != null ? myCustomThread : myDebuggerContext.getThreadProxy();
	}

	public final DebuggerContextImpl getDebuggerContext()
	{
		return myDebuggerContext;
	}

	@Override
	public final void contextAction(@Nonnull SuspendContextImpl suspendContext) throws Exception
	{
		SuspendManager suspendManager = myDebuggerContext.getDebugProcess().getSuspendManager();
		boolean isSuspendedByContext;
		try
		{
			isSuspendedByContext = suspendManager.isSuspended(getThread());
		}
		catch(ObjectCollectedException ignored)
		{
			notifyCancelled();
			return;
		}
		if(isSuspendedByContext)
		{
			if(LOG.isDebugEnabled())
			{
				LOG.debug("Context thread " + suspendContext.getThread());
				LOG.debug("Debug thread" + getThread());
			}
			threadAction(suspendContext);
		}
		else
		{
			// no suspend context currently available
			SuspendContextImpl suspendContextForThread = myCustomThread != null ? suspendContext : SuspendManagerUtil.findContextByThread(suspendManager, getThread());
			if(suspendContextForThread != null)
			{
				suspendContextForThread.postponeCommand(this);
			}
			else
			{
				notifyCancelled();
			}
		}
	}

	/**
	 * @deprecated override {@link #threadAction(SuspendContextImpl)}
	 */
	@Deprecated
	public void threadAction()
	{
		throw new AbstractMethodError();
	}

	public void threadAction(@Nonnull SuspendContextImpl suspendContext)
	{
		//noinspection deprecation
		threadAction();
	}
}
