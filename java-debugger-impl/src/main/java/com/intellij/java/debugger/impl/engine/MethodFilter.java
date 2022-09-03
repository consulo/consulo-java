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
package com.intellij.java.debugger.impl.engine;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.java.debugger.impl.EvaluatingComputable;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.util.Range;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.ObjectReference;

public interface MethodFilter
{
	boolean locationMatches(DebugProcessImpl process, Location location) throws EvaluateException;

	default boolean locationMatches(DebugProcessImpl process, Location location, @Nonnull EvaluatingComputable<ObjectReference> thisProvider) throws EvaluateException
	{
		return locationMatches(process, location);
	}

	@Nullable
	Range<Integer> getCallingExpressionLines();

	default int onReached(SuspendContextImpl context, RequestHint hint)
	{
		return RequestHint.STOP;
	}
}
