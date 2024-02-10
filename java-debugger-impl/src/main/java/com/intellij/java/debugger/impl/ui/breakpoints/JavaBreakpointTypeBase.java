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
package com.intellij.java.debugger.impl.ui.breakpoints;

import com.intellij.java.debugger.impl.JavaDebuggerEditorsProvider;
import com.intellij.java.debugger.impl.breakpoints.JavaBreakpointFiltersPanel;
import com.intellij.java.debugger.impl.breakpoints.properties.JavaBreakpointProperties;
import com.intellij.java.language.psi.PsiClass;
import consulo.application.ReadAction;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.XBreakpointType;
import consulo.execution.debug.breakpoint.ui.XBreakpointCustomPropertiesPanel;
import consulo.execution.debug.evaluation.XDebuggerEditorsProvider;
import consulo.project.Project;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;

/**
 * Base class for non-line java breakpoint
 *
 * @author egor
 */
public abstract class JavaBreakpointTypeBase<T extends JavaBreakpointProperties> extends XBreakpointType<XBreakpoint<T>, T>
{
	protected JavaBreakpointTypeBase(@Nonnull String id, @Nls @Nonnull String title)
	{
		super(id, title, true);
	}

	@Override
	public final boolean isAddBreakpointButtonVisible()
	{
		return true;
	}

	@Nullable
	@Override
	public final XBreakpointCustomPropertiesPanel<XBreakpoint<T>> createCustomRightPropertiesPanel(@Nonnull Project project)
	{
		return new JavaBreakpointFiltersPanel<T, XBreakpoint<T>>(project);
	}

	@Nullable
	@Override
	public final XDebuggerEditorsProvider getEditorsProvider(@Nonnull XBreakpoint<T> breakpoint, @Nonnull Project project)
	{
		return new JavaDebuggerEditorsProvider();
	}

	@Nullable
	@Override
	public XSourcePosition getSourcePosition(@Nonnull XBreakpoint<T> breakpoint)
	{
		Breakpoint javaBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint);
		if(javaBreakpoint != null)
		{
			PsiClass aClass = javaBreakpoint.getPsiClass();
			if(aClass != null && aClass.getContainingFile() != null)
			{
				return ReadAction.compute(() -> XDebuggerUtil.getInstance().createPositionByElement(aClass));
			}
		}
		return null;
	}
}
