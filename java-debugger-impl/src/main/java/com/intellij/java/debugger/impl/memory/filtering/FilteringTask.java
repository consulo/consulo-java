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
package com.intellij.java.debugger.impl.memory.filtering;

import java.util.List;

import jakarta.annotation.Nonnull;

import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import consulo.util.lang.StringUtil;
import consulo.execution.debug.breakpoint.XExpression;
import consulo.internal.com.sun.jdi.ObjectReference;

/**
 * @author Vitaliy.Bibaev
 */
public class FilteringTask implements Runnable
{
	private final List<ObjectReference> myReferences;
	private final ConditionChecker myChecker;
	private final FilteringTaskCallback myCallback;

	private volatile boolean myIsCancelled = false;

	public FilteringTask(@Nonnull String className,
			@Nonnull DebugProcessImpl debugProcess,
			@Nonnull XExpression expression,
			@Nonnull List<ObjectReference> references,
			@Nonnull FilteringTaskCallback callback)
	{
		myChecker = StringUtil.isEmptyOrSpaces(expression.getExpression()) ? ConditionChecker.ALL_MATCHED_CHECKER : new ConditionCheckerImpl(debugProcess, expression, className);
		myReferences = references;
		myCallback = callback;
	}

	public void cancel()
	{
		myIsCancelled = true;
	}

	public boolean isCancelled()
	{
		return myIsCancelled;
	}

	@Override
	public void run()
	{
		myCallback.started(myReferences.size());
		int proceedCount;
		for(proceedCount = 0; proceedCount < myReferences.size() && !myIsCancelled; proceedCount++)
		{
			ObjectReference ref = myReferences.get(proceedCount);
			CheckingResult result = myChecker.check(ref);
			FilteringTaskCallback.Action action = FilteringTaskCallback.Action.CONTINUE;
			switch(result.getResult())
			{
				case MATCH:
					action = myCallback.matched(ref);
					break;
				case NO_MATCH:
					action = myCallback.notMatched(ref);
					break;
				case ERROR:
					action = myCallback.error(ref, result.getFailureDescription());
					break;
			}

			if(action == FilteringTaskCallback.Action.STOP)
			{
				break;
			}
		}

		FilteringResult reason = myIsCancelled ? FilteringResult.INTERRUPTED : proceedCount == myReferences.size() ? FilteringResult.ALL_CHECKED : FilteringResult.LIMIT_REACHED;

		myCallback.completed(reason);
	}
}
