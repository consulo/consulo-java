/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.ui.breakpoints.Breakpoint;
import com.intellij.java.debugger.impl.ui.breakpoints.BreakpointManager;
import com.intellij.java.debugger.impl.ui.breakpoints.JavaExceptionBreakpointType;
import com.intellij.java.debugger.impl.ui.breakpoints.JavaFieldBreakpointType;
import com.intellij.java.debugger.impl.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.java.debugger.impl.ui.breakpoints.JavaMethodBreakpointType;
import com.intellij.java.debugger.impl.ui.breakpoints.JavaWildcardMethodBreakpointType;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.XBreakpointHandler;
import consulo.execution.debug.breakpoint.XBreakpointType;

/**
 * @author egor
 */
public class JavaBreakpointHandler extends XBreakpointHandler
{
	protected final DebugProcessImpl myProcess;

	public JavaBreakpointHandler(@Nonnull Class<? extends XBreakpointType<?, ?>> breakpointTypeClass, DebugProcessImpl process)
	{
		super(breakpointTypeClass);
		myProcess = process;
	}

	@Nullable
	protected Breakpoint createJavaBreakpoint(@Nonnull XBreakpoint xBreakpoint)
	{
		return null;
	}

	@Override
	public void registerBreakpoint(@Nonnull XBreakpoint breakpoint)
	{
		Breakpoint javaBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint);
		if(javaBreakpoint == null)
		{
			javaBreakpoint = createJavaBreakpoint(breakpoint);
			breakpoint.putUserData(Breakpoint.DATA_KEY, javaBreakpoint);
		}
		if(javaBreakpoint != null)
		{
			final Breakpoint bpt = javaBreakpoint;
			BreakpointManager.addBreakpoint(bpt);
			// use schedule not to block initBreakpoints
			myProcess.getManagerThread().schedule(new DebuggerCommandImpl()
			{
				@Override
				protected void action() throws Exception
				{
					bpt.createRequest(myProcess);
				}

				@Override
				public Priority getPriority()
				{
					return Priority.HIGH;
				}
			});
		}
	}

	@Override
	public void unregisterBreakpoint(@Nonnull final XBreakpoint breakpoint, boolean temporary)
	{
		final Breakpoint javaBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint);
		if(javaBreakpoint != null)
		{
			// use schedule not to block initBreakpoints
			myProcess.getManagerThread().schedule(new DebuggerCommandImpl()
			{
				@Override
				protected void action() throws Exception
				{
					myProcess.getRequestsManager().deleteRequest(javaBreakpoint);
				}

				@Override
				public Priority getPriority()
				{
					return Priority.HIGH;
				}
			});
		}
	}

	public static class JavaLineBreakpointHandler extends JavaBreakpointHandler
	{
		public JavaLineBreakpointHandler(DebugProcessImpl process)
		{
			super(JavaLineBreakpointType.class, process);
		}
	}

	public static class JavaExceptionBreakpointHandler extends JavaBreakpointHandler
	{
		public JavaExceptionBreakpointHandler(DebugProcessImpl process)
		{
			super(JavaExceptionBreakpointType.class, process);
		}
	}

	public static class JavaMethodBreakpointHandler extends JavaBreakpointHandler
	{
		public JavaMethodBreakpointHandler(DebugProcessImpl process)
		{
			super(JavaMethodBreakpointType.class, process);
		}
	}

	public static class JavaWildcardBreakpointHandler extends JavaBreakpointHandler
	{
		public JavaWildcardBreakpointHandler(DebugProcessImpl process)
		{
			super(JavaWildcardMethodBreakpointType.class, process);
		}
	}

	public static class JavaFieldBreakpointHandler extends JavaBreakpointHandler
	{
		public JavaFieldBreakpointHandler(DebugProcessImpl process)
		{
			super(JavaFieldBreakpointType.class, process);
		}
	}
}
