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
package com.intellij.java.debugger.impl.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import jakarta.annotation.Nonnull;

import com.intellij.java.debugger.engine.SuspendContext;
import consulo.logging.Logger;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.MagicConstant;

import consulo.application.util.Patches;
import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import consulo.application.util.concurrent.ThreadDumper;
import consulo.util.lang.Comparing;
import java.util.HashSet;
import consulo.execution.debug.frame.XSuspendContext;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.ThreadReference;
import consulo.internal.com.sun.jdi.event.EventSet;
import consulo.internal.com.sun.jdi.request.EventRequest;

/**
 * @author lex
 */
public abstract class SuspendContextImpl extends XSuspendContext implements SuspendContext
{
	private static final Logger LOG = Logger.getInstance(SuspendContextImpl.class);

	private final DebugProcessImpl myDebugProcess;
	private final int mySuspendPolicy;

	private ThreadReferenceProxyImpl myThread;
	boolean myIsVotedForResume = true;

	protected int myVotesToVote;
	protected Set<ThreadReferenceProxyImpl> myResumedThreads;

	private final EventSet myEventSet;
	private volatile boolean myIsResumed;

	private final ConcurrentLinkedQueue<SuspendContextCommandImpl> myPostponedCommands = new ConcurrentLinkedQueue<>();
	public volatile boolean myInProgress;
	private final HashSet<ObjectReference> myKeptReferences = new HashSet<>();
	private EvaluationContextImpl myEvaluationContext = null;

	private JavaExecutionStack myActiveExecutionStack;

	SuspendContextImpl(@jakarta.annotation.Nonnull DebugProcessImpl debugProcess, @MagicConstant(flagsFromClass = EventRequest.class) int suspendPolicy, int eventVotes, EventSet set)
	{
		myDebugProcess = debugProcess;
		mySuspendPolicy = suspendPolicy;
		myVotesToVote = eventVotes;
		myEventSet = set;
	}

	public void setThread(ThreadReference thread)
	{
		assertNotResumed();
		ThreadReferenceProxyImpl threadProxy = myDebugProcess.getVirtualMachineProxy().getThreadReferenceProxy(thread);
		LOG.assertTrue(myThread == null || myThread == threadProxy);
		myThread = threadProxy;
	}

	protected abstract void resumeImpl();

	protected void resume()
	{
		assertNotResumed();
		if(isEvaluating())
		{
			LOG.error("Resuming context while evaluating", ThreadDumper.dumpThreadsToString());
		}
		DebuggerManagerThreadImpl.assertIsManagerThread();
		try
		{
			if(!Patches.IBM_JDK_DISABLE_COLLECTION_BUG)
			{
				myKeptReferences.forEach(DebuggerUtilsEx::enableCollection);
				myKeptReferences.clear();
			}

			for(SuspendContextCommandImpl cmd = pollPostponedCommand(); cmd != null; cmd = pollPostponedCommand())
			{
				cmd.notifyCancelled();
			}

			resumeImpl();
		}
		finally
		{
			myIsResumed = true;
		}
	}

	private void assertNotResumed()
	{
		if(myIsResumed)
		{
			if(myDebugProcess.isAttached())
			{
				LOG.error("Cannot access SuspendContext. SuspendContext is resumed.");
			}
		}
	}


	@jakarta.annotation.Nullable
	public EventSet getEventSet()
	{
		return myEventSet;
	}

	@Override
	@Nonnull
	public DebugProcessImpl getDebugProcess()
	{
		return myDebugProcess;
	}

	@Override
	public StackFrameProxyImpl getFrameProxy()
	{
		assertNotResumed();
		try
		{
			return myThread != null && myThread.frameCount() > 0 ? myThread.frame(0) : null;
		}
		catch(EvaluateException ignored)
		{
			return null;
		}
	}

	@Nullable
	@Override
	public ThreadReferenceProxyImpl getThread()
	{
		return myThread;
	}

	@MagicConstant(flagsFromClass = EventRequest.class)
	@Override
	public int getSuspendPolicy()
	{
		return mySuspendPolicy;
	}

	public void doNotResumeHack()
	{
		assertNotResumed();
		myVotesToVote = 1000000000;
	}

	public boolean isExplicitlyResumed(@jakarta.annotation.Nullable ThreadReferenceProxyImpl thread)
	{
		return myResumedThreads != null && myResumedThreads.contains(thread);
	}

	public boolean suspends(ThreadReferenceProxyImpl thread)
	{
		assertNotResumed();
		if(isEvaluating())
		{
			return false;
		}
		switch(getSuspendPolicy())
		{
			case EventRequest.SUSPEND_ALL:
				return !isExplicitlyResumed(thread);
			case EventRequest.SUSPEND_EVENT_THREAD:
				return thread == getThread();
		}
		return false;
	}

	public boolean isEvaluating()
	{
		assertNotResumed();
		return myEvaluationContext != null;
	}

	public EvaluationContextImpl getEvaluationContext()
	{
		return myEvaluationContext;
	}

	public boolean isResumed()
	{
		return myIsResumed;
	}

	public void setIsEvaluating(EvaluationContextImpl evaluationContext)
	{
		assertNotResumed();
		myEvaluationContext = evaluationContext;
	}

	public String toString()
	{
		if(myEventSet != null)
		{
			return myEventSet.toString();
		}
		return myThread != null ? myThread.toString() : DebuggerBundle.message("string.null.context");
	}

	public void keep(ObjectReference reference)
	{
		if(!Patches.IBM_JDK_DISABLE_COLLECTION_BUG)
		{
			final boolean added = myKeptReferences.add(reference);
			if(added)
			{
				DebuggerUtilsEx.disableCollection(reference);
			}
		}
	}

	public final void postponeCommand(final SuspendContextCommandImpl command)
	{
		if(!isResumed())
		{
			// Important! when postponing increment the holds counter, so that the action is not released too early.
			// This will ensure that the counter becomes zero only when the command is actually executed or canceled
			command.hold();
			myPostponedCommands.add(command);
		}
		else
		{
			command.notifyCancelled();
		}
	}

	public final SuspendContextCommandImpl pollPostponedCommand()
	{
		return myPostponedCommands.poll();
	}

	@jakarta.annotation.Nullable
	@Override
	public JavaExecutionStack getActiveExecutionStack()
	{
		return myActiveExecutionStack;
	}

	public void initExecutionStacks(ThreadReferenceProxyImpl activeThread)
	{
		DebuggerManagerThreadImpl.assertIsManagerThread();
		if(myThread == null)
		{
			myThread = activeThread;
		}
		if(activeThread != null)
		{
			myActiveExecutionStack = new JavaExecutionStack(activeThread, myDebugProcess, myThread == activeThread);
			myActiveExecutionStack.initTopFrame();
		}
	}

	@Override
	public void computeExecutionStacks(final XExecutionStackContainer container)
	{
		myDebugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(this)
		{
			@Override
			public void contextAction(@Nonnull SuspendContextImpl suspendContext) throws Exception
			{
				List<JavaExecutionStack> res = new ArrayList<>();
				Collection<ThreadReferenceProxyImpl> threads = getDebugProcess().getVirtualMachineProxy().allThreads();
				JavaExecutionStack currentStack = null;
				for(ThreadReferenceProxyImpl thread : threads)
				{
					boolean current = thread == myThread;
					JavaExecutionStack stack = new JavaExecutionStack(thread, myDebugProcess, current);
					if(!current)
					{
						res.add(stack);
					}
					else
					{
						currentStack = stack;
					}
				}
				res.sort(THREADS_COMPARATOR);
				if(currentStack != null)
				{
					res.add(0, currentStack);
				}
				container.addExecutionStack(res, true);
			}
		});
	}

	private static final Comparator<JavaExecutionStack> THREADS_COMPARATOR = (th1, th2) ->
	{
		int res = Comparing.compare(th2.getThreadProxy().isSuspended(), th1.getThreadProxy().isSuspended());
		if(res == 0)
		{
			return th1.getDisplayName().compareToIgnoreCase(th2.getDisplayName());
		}
		return res;
	};
}
