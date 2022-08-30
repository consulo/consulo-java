/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.io.OutputStream;

import com.intellij.java.debugger.DebuggerManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.DebugProcessAdapter;
import com.intellij.openapi.project.Project;

public class RemoteDebugProcessHandler extends ProcessHandler
{
	private final Project myProject;

	public RemoteDebugProcessHandler(Project project)
	{
		myProject = project;
	}

	@Override
	public void startNotify()
	{
		final DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
		final DebugProcessAdapter listener = new DebugProcessAdapter()
		{
			//executed in manager thread
			@Override
			public void processDetached(DebugProcess process, boolean closedByUser)
			{
				debugProcess.removeDebugProcessListener(this);
				notifyProcessDetached();
			}
		};
		debugProcess.addDebugProcessListener(listener);
		try
		{
			super.startNotify();
		}
		finally
		{
			// in case we added our listener too late, we may have lost processDetached notification,
			// so check here if process is detached
			if(debugProcess.isDetached())
			{
				debugProcess.removeDebugProcessListener(listener);
				notifyProcessDetached();
			}
		}
	}

	@Override
	protected void destroyProcessImpl()
	{
		DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
		if(debugProcess != null)
		{
			debugProcess.stop(true);
		}
	}

	@Override
	protected void detachProcessImpl()
	{
		DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
		if(debugProcess != null)
		{
			debugProcess.stop(false);
		}
	}

	@Override
	public boolean detachIsDefault()
	{
		return true;
	}

	@Override
	public OutputStream getProcessInput()
	{
		return null;
	}
}
