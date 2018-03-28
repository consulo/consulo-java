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
package com.intellij.debugger.ui.breakpoints;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;
import org.jetbrains.java.debugger.breakpoints.JavaBreakpointFiltersPanel;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;

/**
 * Base class for java line-connected exceptions (line, method, field)
 *
 * @author egor
 */
public abstract class JavaLineBreakpointTypeBase<P extends JavaBreakpointProperties> extends XLineBreakpointType<P>
{
	public JavaLineBreakpointTypeBase(@NonNls @Nonnull String id, @Nls @Nonnull String title)
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

	@javax.annotation.Nullable
	@Override
	public final XBreakpointCustomPropertiesPanel<XLineBreakpoint<P>> createCustomRightPropertiesPanel(@Nonnull Project project)
	{
		return new JavaBreakpointFiltersPanel<P, XLineBreakpoint<P>>(project);
	}

	@javax.annotation.Nullable
	@Override
	public final XDebuggerEditorsProvider getEditorsProvider(@Nonnull XLineBreakpoint<P> breakpoint, @Nonnull Project project)
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
