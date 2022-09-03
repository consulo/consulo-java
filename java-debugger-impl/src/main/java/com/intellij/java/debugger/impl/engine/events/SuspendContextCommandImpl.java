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
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.engine.managerThread.SuspendContextCommand;
import consulo.logging.Logger;
import consulo.util.collection.Stack;

/**
 * Performs contextAction when evaluation is available in suspend context
 */
public abstract class SuspendContextCommandImpl extends DebuggerCommandImpl
{
	private static final Logger LOG = Logger.getInstance(SuspendContextCommand.class);

	private final SuspendContextImpl mySuspendContext;

	protected SuspendContextCommandImpl(@Nullable SuspendContextImpl suspendContext)
	{
		mySuspendContext = suspendContext;
	}

	/**
	 * @deprecated override {@link #contextAction(SuspendContextImpl)}
	 */
	@Deprecated
	public void contextAction() throws Exception
	{
		throw new AbstractMethodError();
	}

	public void contextAction(@Nonnull SuspendContextImpl suspendContext) throws Exception
	{
		//noinspection deprecation
		contextAction();
	}

	@Override
	public final void action() throws Exception
	{
		if(LOG.isDebugEnabled())
		{
			LOG.debug("trying " + this);
		}

		final SuspendContextImpl suspendContext = getSuspendContext();
		if(suspendContext == null)
		{
			if(LOG.isDebugEnabled())
			{
				LOG.debug("skip processing - context is null " + this);
			}
			notifyCancelled();
			return;
		}

		if(suspendContext.myInProgress)
		{
			suspendContext.postponeCommand(this);
		}
		else
		{
			try
			{
				if(!suspendContext.isResumed())
				{
					suspendContext.myInProgress = true;
					contextAction(suspendContext);
				}
				else
				{
					notifyCancelled();
				}
			}
			finally
			{
				suspendContext.myInProgress = false;
				if(suspendContext.isResumed())
				{
					for(SuspendContextCommandImpl postponed = suspendContext.pollPostponedCommand(); postponed != null; postponed = suspendContext.pollPostponedCommand())
					{
						postponed.notifyCancelled();
					}
				}
				else
				{
					SuspendContextCommandImpl postponed = suspendContext.pollPostponedCommand();
					if(postponed != null)
					{
						final Stack<SuspendContextCommandImpl> stack = new Stack<>();
						while(postponed != null)
						{
							stack.push(postponed);
							postponed = suspendContext.pollPostponedCommand();
						}
						final DebuggerManagerThreadImpl managerThread = suspendContext.getDebugProcess().getManagerThread();
						while(!stack.isEmpty())
						{
							managerThread.pushBack(stack.pop());
						}
					}
				}
			}
		}
	}

	@Nullable
	public SuspendContextImpl getSuspendContext()
	{
		return mySuspendContext;
	}
}
