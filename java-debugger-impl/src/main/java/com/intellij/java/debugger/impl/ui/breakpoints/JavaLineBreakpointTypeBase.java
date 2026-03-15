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
package com.intellij.java.debugger.impl.ui.breakpoints;

import org.jspecify.annotations.Nullable;

import consulo.execution.debug.breakpoint.ui.XBreakpointCustomPropertiesPanel;
import consulo.execution.debug.evaluation.XDebuggerEditorsProvider;
import com.intellij.java.debugger.impl.JavaDebuggerEditorsProvider;
import com.intellij.java.debugger.impl.breakpoints.JavaBreakpointFiltersPanel;
import com.intellij.java.debugger.impl.breakpoints.properties.JavaBreakpointProperties;
import consulo.project.Project;
import consulo.execution.debug.breakpoint.XLineBreakpoint;
import consulo.execution.debug.breakpoint.XLineBreakpointType;

/**
 * Base class for java line-connected exceptions (line, method, field)
 *
 * @author egor
 */
public abstract class JavaLineBreakpointTypeBase<P extends JavaBreakpointProperties> extends XLineBreakpointType<P>
{
	public JavaLineBreakpointTypeBase(String id, String title)
	{
		super(id, title);
	}

	@Override
	public boolean isAddBreakpointButtonVisible()
	{
		return false;
	}

	@Override
	public final boolean isSuspendThreadSupported()
	{
		return true;
	}

	@Nullable
	@Override
	public final XBreakpointCustomPropertiesPanel<XLineBreakpoint<P>> createCustomRightPropertiesPanel(Project project)
	{
		return new JavaBreakpointFiltersPanel<P, XLineBreakpoint<P>>(project);
	}

	@Nullable
	@Override
	public final XDebuggerEditorsProvider getEditorsProvider(XLineBreakpoint<P> breakpoint, Project project)
	{
		return new JavaDebuggerEditorsProvider();
	}

	@Override
	public String getDisplayText(XLineBreakpoint<P> breakpoint)
	{
		BreakpointWithHighlighter javaBreakpoint = (BreakpointWithHighlighter) BreakpointManager.getJavaBreakpoint(breakpoint);
		if(javaBreakpoint != null)
		{
			return javaBreakpoint.getDescription();
		}
		else
		{
			return super.getDisplayText(breakpoint);
		}
	}
}
