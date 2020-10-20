/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebugUIEnvironment;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DefaultDebugUIEnvironment;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.tree.render.BatchEvaluator;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentWithExecutorListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;

@Singleton
public class DebuggerPanelsManager
{
	private final Project myProject;

	@Inject
	public DebuggerPanelsManager(Project project)
	{
		myProject = project;
		if(project.isDefault())
		{
			return;
		}

		myProject.getMessageBus().connect(myProject).subscribe(RunContentManager.TOPIC, new RunContentWithExecutorListener()
		{
			@Override
			public void contentSelected(@Nullable RunContentDescriptor descriptor, @Nonnull Executor executor)
			{
				if(executor == DefaultDebugExecutor.getDebugExecutorInstance())
				{
					DebuggerSession session = descriptor == null ? null : getSession(myProject, descriptor);
					if(session != null)
					{
						getContextManager().setState(session.getContextManager().getContext(), session.getState(), DebuggerSession.Event.CONTEXT, null);
					}
					else
					{
						getContextManager().setState(DebuggerContextImpl.EMPTY_CONTEXT, DebuggerSession.State.DISPOSED, DebuggerSession.Event.CONTEXT, null);
					}
				}
			}

			@Override
			public void contentRemoved(@Nullable RunContentDescriptor descriptor, @Nonnull Executor executor)
			{
			}
		});
	}

	private DebuggerStateManager getContextManager()
	{
		return DebuggerManagerEx.getInstanceEx(myProject).getContextManager();
	}

	@Nullable
	public RunContentDescriptor attachVirtualMachine(@Nonnull ExecutionEnvironment environment,
													 RunProfileState state,
													 RemoteConnection remoteConnection,
													 boolean pollConnection) throws ExecutionException
	{
		return attachVirtualMachine(new DefaultDebugUIEnvironment(environment, state, remoteConnection, pollConnection));
	}

	@Nullable
	public RunContentDescriptor attachVirtualMachine(DebugUIEnvironment environment) throws ExecutionException
	{
		final DebugEnvironment modelEnvironment = environment.getEnvironment();
		final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(myProject).attachVirtualMachine(modelEnvironment);
		if(debuggerSession == null)
		{
			return null;
		}

		final DebugProcessImpl debugProcess = debuggerSession.getProcess();
		if(debugProcess.isDetached() || debugProcess.isDetaching())
		{
			debuggerSession.dispose();
			return null;
		}
		if(modelEnvironment.isRemote())
		{
			// optimization: that way BatchEvaluator will not try to lookup the class file in remote VM
			// which is an expensive operation when executed first time
			debugProcess.putUserData(BatchEvaluator.REMOTE_SESSION_KEY, Boolean.TRUE);
		}

		XDebugSession debugSession = XDebuggerManager.getInstance(myProject).startSessionAndShowTab(modelEnvironment.getSessionName(), environment.getReuseContent(), new XDebugProcessStarter()
		{
			@Override
			@Nonnull
			public XDebugProcess start(@Nonnull XDebugSession session)
			{
				return JavaDebugProcess.create(session, debuggerSession);
			}
		});
		return debugSession.getRunContentDescriptor();
	}

	public static DebuggerPanelsManager getInstance(Project project)
	{
		return project.getComponent(DebuggerPanelsManager.class);
	}

	private static DebuggerSession getSession(Project project, RunContentDescriptor descriptor)
	{
		for(JavaDebugProcess process : XDebuggerManager.getInstance(project).getDebugProcesses(JavaDebugProcess.class))
		{
			if(Comparing.equal(process.getProcessHandler(), descriptor.getProcessHandler()))
			{
				return process.getDebuggerSession();
			}
		}
		return null;
	}
}
