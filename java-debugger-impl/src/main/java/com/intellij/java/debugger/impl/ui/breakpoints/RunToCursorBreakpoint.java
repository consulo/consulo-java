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
package com.intellij.java.debugger.impl.ui.breakpoints;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.java.debugger.impl.breakpoints.properties.JavaLineBreakpointProperties;
import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import consulo.execution.debug.XSourcePosition;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.execution.debug.breakpoint.XLineBreakpointType;
import consulo.internal.com.sun.jdi.event.LocatableEvent;
import consulo.xdebugger.breakpoints.XLineBreakpointResolverTypeExtension;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 13, 2006
 */
public class RunToCursorBreakpoint extends LineBreakpoint<JavaLineBreakpointProperties>
{
	private final boolean myRestoreBreakpoints;
	@Nonnull
	protected final SourcePosition myCustomPosition;
	private String mySuspendPolicy;
	private final JavaLineBreakpointProperties myProperties = new JavaLineBreakpointProperties();

	protected RunToCursorBreakpoint(@Nonnull Project project, @Nonnull SourcePosition pos, boolean restoreBreakpoints)
	{
		super(project, null);
		myCustomPosition = pos;
		setVisible(false);
		myRestoreBreakpoints = restoreBreakpoints;
	}

	@Nonnull
	@Override
	public SourcePosition getSourcePosition()
	{
		return myCustomPosition;
	}

	@Override
	public int getLineIndex()
	{
		return myCustomPosition.getLine();
	}

	@Override
	public void reload()
	{
	}

	@Override
	public String getSuspendPolicy()
	{
		return mySuspendPolicy;
	}

	public void setSuspendPolicy(String policy)
	{
		mySuspendPolicy = policy;
	}

	protected boolean isLogEnabled()
	{
		return false;
	}

	@Override
	protected boolean isLogExpressionEnabled()
	{
		return false;
	}

	@Override
	public boolean isEnabled()
	{
		return true;
	}

	public boolean isCountFilterEnabled()
	{
		return false;
	}

	public boolean isClassFiltersEnabled()
	{
		return false;
	}

	@Override
	public boolean isConditionEnabled()
	{
		return false;
	}

	public boolean isRestoreBreakpoints()
	{
		return myRestoreBreakpoints;
	}

	@Override
	public String getEventMessage(LocatableEvent event)
	{
		return DebuggerBundle.message("status.stopped.at.cursor");
	}

	@Override
	protected boolean isVisible()
	{
		return false;
	}

	@Override
	public boolean isValid()
	{
		return true;
	}

	@Nonnull
	@Override
	protected JavaLineBreakpointProperties getProperties()
	{
		return myProperties;
	}

	@Override
	protected void fireBreakpointChanged()
	{
	}

	@Override
	protected boolean isMuted(@Nonnull final DebugProcessImpl debugProcess)
	{
		return false;  // always enabled
	}

	@Nullable
	@Override
	protected JavaLineBreakpointType getXBreakpointType()
	{
		SourcePosition position = getSourcePosition();
		VirtualFile file = position.getFile().getVirtualFile();
		int line = position.getLine();

		if(file == null)
		{
			return null;
		}

		XLineBreakpointType<?> breakpointType = XLineBreakpointResolverTypeExtension.INSTANCE.resolveBreakpointType(myProject, file, line);
		if(breakpointType instanceof JavaLineBreakpointType)
		{
			return (JavaLineBreakpointType) breakpointType;
		}
		return null;
	}

	@Nullable
	protected static RunToCursorBreakpoint create(@Nonnull Project project, @Nonnull XSourcePosition position, boolean restoreBreakpoints)
	{
		PsiFile psiFile = PsiManager.getInstance(project).findFile(position.getFile());
		if(psiFile == null)
		{
			return null;
		}
		return new RunToCursorBreakpoint(project, SourcePosition.createFromOffset(psiFile, position.getOffset()), restoreBreakpoints);
	}
}
