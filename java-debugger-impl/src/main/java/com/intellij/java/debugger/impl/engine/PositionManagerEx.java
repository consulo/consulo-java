/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import jakarta.annotation.Nonnull;
import com.intellij.java.debugger.PositionManager;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import consulo.execution.debug.frame.XStackFrame;
import consulo.util.lang.ThreeState;
import consulo.internal.com.sun.jdi.Location;
import jakarta.annotation.Nullable;

public abstract class PositionManagerEx implements PositionManager
{
	@Nullable
	public abstract XStackFrame createStackFrame(@jakarta.annotation.Nonnull StackFrameProxyImpl frame, @jakarta.annotation.Nonnull DebugProcessImpl debugProcess, @jakarta.annotation.Nonnull Location location);

	public abstract ThreeState evaluateCondition(@jakarta.annotation.Nonnull EvaluationContext context, @Nonnull StackFrameProxyImpl frame, @jakarta.annotation.Nonnull Location location, @jakarta.annotation.Nonnull String expression);
}
