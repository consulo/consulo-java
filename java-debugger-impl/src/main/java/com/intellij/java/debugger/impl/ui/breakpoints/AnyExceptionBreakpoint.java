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

/*
 * Class AnyExceptionBreakpoint
 * @author Jeka
 */
package com.intellij.java.debugger.impl.ui.breakpoints;

import consulo.execution.debug.breakpoint.XBreakpoint;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import consulo.project.Project;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.dataholder.Key;
import consulo.internal.com.sun.jdi.ReferenceType;

public class AnyExceptionBreakpoint extends ExceptionBreakpoint
{
	public static final
	@NonNls
	Key<AnyExceptionBreakpoint> ANY_EXCEPTION_BREAKPOINT = BreakpointCategory.lookup("breakpoint_any");

	protected AnyExceptionBreakpoint(Project project, XBreakpoint xBreakpoint)
	{
		super(project, null, null, xBreakpoint);
		//setEnabled(false);
	}

	@Override
	public Key<AnyExceptionBreakpoint> getCategory()
	{
		return ANY_EXCEPTION_BREAKPOINT;
	}

	@Override
	public String getDisplayName()
	{
		return DebuggerBundle.message("breakpoint.any.exception.display.name");
	}

	@Override
	public void createRequest(DebugProcessImpl debugProcess)
	{
		DebuggerManagerThreadImpl.assertIsManagerThread();
		if(!isEnabled() || !debugProcess.isAttached() || debugProcess.areBreakpointsMuted() || !debugProcess.getRequestsManager().findRequests(this)
				.isEmpty())
		{
			return;
		}
		super.processClassPrepare(debugProcess, null);
	}

	@Override
	public void processClassPrepare(DebugProcess debugProcess, ReferenceType refType)
	{
		// should be emty - does not make sense for this breakpoint
	}

	@Override
	public void readExternal(Element parentNode) throws InvalidDataException
	{
		try
		{
			super.readExternal(parentNode);
		}
		catch(InvalidDataException e)
		{
			if(!READ_NO_CLASS_NAME.equals(e.getMessage()))
			{
				throw e;
			}
		}
	}

}