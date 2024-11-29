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
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.engine.managerThread.DebuggerCommand;
import com.intellij.java.debugger.engine.managerThread.DebuggerManagerThread;
import com.intellij.java.debugger.engine.managerThread.SuspendContextCommand;
import com.intellij.java.debugger.impl.InvokeAndWaitThread;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicatorListener;
import consulo.application.progress.ProgressManager;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.openapi.progress.util.ProgressWindow;
import consulo.ide.impl.idea.openapi.progress.util.ProgressWindowWithNotification;
import consulo.internal.com.sun.jdi.VMDisconnectedException;
import consulo.logging.Logger;
import consulo.project.Project;
import org.jetbrains.annotations.TestOnly;

import jakarta.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

/**
 * @author lex
 */
public class DebuggerManagerThreadImpl extends InvokeAndWaitThread<DebuggerCommandImpl> implements DebuggerManagerThread, Disposable
{
	private static final Logger LOG = Logger.getInstance(DebuggerManagerThreadImpl.class);
	static final int COMMAND_TIMEOUT = 3000;

	private volatile boolean myDisposed;

	DebuggerManagerThreadImpl(@Nonnull Disposable parent, Project project)
	{
		super(project);
		Disposer.register(parent, this);
	}

	@Override
	public void dispose()
	{
		myDisposed = true;
	}

	@TestOnly
	public static DebuggerManagerThreadImpl createTestInstance(@Nonnull Disposable parent, Project project)
	{
		return new DebuggerManagerThreadImpl(parent, project);
	}

	public static boolean isManagerThread()
	{
		return currentThread() instanceof DebuggerManagerThreadImpl;
	}

	public static void assertIsManagerThread()
	{
		LOG.assertTrue(isManagerThread(), "Should be invoked in manager thread, use DebuggerManagerThreadImpl.getInstance(..).invoke...");
	}

	@Override
	public void invokeAndWait(DebuggerCommandImpl managerCommand)
	{
		LOG.assertTrue(!isManagerThread(), "Should be invoked outside manager thread, use DebuggerManagerThreadImpl.getInstance(..).invoke...");
		super.invokeAndWait(managerCommand);
	}

	public void invoke(DebuggerCommandImpl managerCommand)
	{
		if(currentThread() == this)
		{
			processEvent(managerCommand);
		}
		else
		{
			schedule(managerCommand);
		}
	}

	@Override
	public boolean pushBack(DebuggerCommandImpl managerCommand)
	{
		final boolean pushed = super.pushBack(managerCommand);
		if(!pushed)
		{
			managerCommand.notifyCancelled();
		}
		return pushed;
	}

	@Override
	public boolean schedule(DebuggerCommandImpl managerCommand)
	{
		final boolean scheduled = super.schedule(managerCommand);
		if(!scheduled)
		{
			managerCommand.notifyCancelled();
		}
		return scheduled;
	}

	/**
	 * waits COMMAND_TIMEOUT milliseconds
	 * if worker thread is still processing the same command
	 * calls terminateCommand
	 */
	public void terminateAndInvoke(DebuggerCommandImpl command, int terminateTimeoutMillis)
	{
		final DebuggerCommandImpl currentCommand = myEvents.getCurrentEvent();

		invoke(command);

		if(currentCommand != null)
		{
			AppExecutorUtil.getAppScheduledExecutorService().schedule(() ->
			{
				if(currentCommand == myEvents.getCurrentEvent())
				{
					// if current command is still in progress, cancel it
					getCurrentRequest().requestStop();
					try
					{
						getCurrentRequest().join();
					}
					catch(InterruptedException ignored)
					{
					}
					catch(Exception e)
					{
						throw new RuntimeException(e);
					}
					finally
					{
						if(!myDisposed)
						{
							startNewWorkerThread();
						}
					}
				}
			}, terminateTimeoutMillis, TimeUnit.MILLISECONDS);
		}
	}

	@Override
	public void processEvent(@Nonnull DebuggerCommandImpl managerCommand)
	{
		assertIsManagerThread();
		try
		{
			if(myEvents.isClosed())
			{
				managerCommand.notifyCancelled();
			}
			else
			{
				managerCommand.run();
			}
		}
		catch(VMDisconnectedException e)
		{
			LOG.debug(e);
		}
		catch(RuntimeException e)
		{
			throw e;
		}
		catch(InterruptedException e)
		{
			throw new RuntimeException(e);
		}
		catch(Exception e)
		{
			LOG.error(e);
		}
	}

	public void startProgress(DebuggerCommandImpl command, ProgressWindow progressWindow)
	{
		progressWindow.addListener(new ProgressIndicatorListener()
		{
			@Override
			public void canceled()
			{
				command.release();
			}
		});

		ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressManager.getInstance().runProcess(() -> invokeAndWait(command), progressWindow));
	}


	void startLongProcessAndFork(Runnable process)
	{
		assertIsManagerThread();
		startNewWorkerThread();

		try
		{
			process.run();
		}
		finally
		{
			final WorkerThreadRequest request = getCurrentThreadRequest();

			if(LOG.isDebugEnabled())
			{
				LOG.debug("Switching back to " + request);
			}

			super.invokeAndWait(new DebuggerCommandImpl()
			{
				@Override
				protected void action() throws Exception
				{
					switchToRequest(request);
				}

				@Override
				protected void commandCancelled()
				{
					LOG.debug("Event queue was closed, killing request");
					request.requestStop();
				}
			});
		}
	}

	@Override
	public void invokeCommand(final DebuggerCommand command)
	{
		if(command instanceof SuspendContextCommand)
		{
			SuspendContextCommand suspendContextCommand = (SuspendContextCommand) command;
			schedule(new SuspendContextCommandImpl((SuspendContextImpl) suspendContextCommand.getSuspendContext())
			{
				@Override
				public void contextAction() throws Exception
				{
					command.action();
				}

				@Override
				protected void commandCancelled()
				{
					command.commandCancelled();
				}
			});
		}
		else
		{
			schedule(new DebuggerCommandImpl()
			{
				@Override
				protected void action() throws Exception
				{
					command.action();
				}

				@Override
				protected void commandCancelled()
				{
					command.commandCancelled();
				}
			});
		}

	}

	void restartIfNeeded()
	{
		if(myEvents.isClosed())
		{
			myEvents.reopen();
			startNewWorkerThread();
		}
	}
}
