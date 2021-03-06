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
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.MethodFilter;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author egor
 */
public abstract class JvmSteppingCommandProvider
{
	public static final ExtensionPointName<JvmSteppingCommandProvider> EP_NAME = ExtensionPointName.create("consulo.java.debugger.jvmSteppingCommandProvider");

	/**
	 * @return null if can not handle
	 */
	public DebugProcessImpl.ResumeCommand getStepIntoCommand(SuspendContextImpl suspendContext, boolean ignoreFilters, final MethodFilter smartStepFilter, int stepSize)
	{
		return null;
	}

	/**
	 * @return null if can not handle
	 */
	public DebugProcessImpl.ResumeCommand getStepOutCommand(SuspendContextImpl suspendContext, int stepSize)
	{
		return null;
	}

	/**
	 * @return null if can not handle
	 */
	public DebugProcessImpl.ResumeCommand getStepOverCommand(SuspendContextImpl suspendContext, boolean ignoreBreakpoints, int stepSize)
	{
		return null;
	}
}
