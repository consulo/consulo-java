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
package com.intellij.debugger.ui.breakpoints;

import java.util.List;

import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import consulo.internal.com.sun.jdi.Method;

/**
 * @author egor
 */
public interface MethodBreakpointBase extends FilteredRequestor
{
	XBreakpoint<JavaMethodBreakpointProperties> getXBreakpoint();

	boolean isWatchEntry();

	boolean isWatchExit();

	List<Method> matchingMethods(List<Method> methods, DebugProcessImpl debugProcess);

	void disableEmulation();

	static void disableEmulation(Breakpoint<JavaMethodBreakpointProperties> breakpoint)
	{
		ApplicationManager.getApplication().invokeLater(() ->
		{
			breakpoint.getProperties().EMULATED = false;
			breakpoint.fireBreakpointChanged();
		});
	}
}
