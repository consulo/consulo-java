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
package com.intellij.java.debugger.impl.ui.impl.watch;

import java.util.HashMap;
import java.util.Map;

import consulo.internal.com.sun.jdi.Method;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 13, 2006
 */
public class MethodsTracker
{
	private final Map<Method, Integer> myMethodToOccurrenceMap = new HashMap<Method, Integer>();
	private final Map<Integer, MethodOccurrence> myOccurences = new HashMap<Integer, MethodOccurrence>();

	public final class MethodOccurrence
	{
		private final Method myMethod;
		private final int myIndex;

		private MethodOccurrence(Method method, int index)
		{
			myMethod = method;
			myIndex = index;
		}

		public Method getMethod()
		{
			return myMethod;
		}

		public int getIndex()
		{
			return getOccurrenceCount(myMethod) - myIndex;
		}

		public boolean isRecursive()
		{
			return getOccurrenceCount(myMethod) > 1;
		}
	}

	public MethodOccurrence getMethodOccurrence(int frameIndex, Method method)
	{
		MethodOccurrence occurrence = myOccurences.get(frameIndex);
		if(occurrence == null)
		{
			occurrence = new MethodOccurrence(method, assignOccurrenceIndex(method));
			myOccurences.put(frameIndex, occurrence);
		}
		return occurrence;
	}

	private int getOccurrenceCount(Method method)
	{
		if(method == null)
		{
			return 0;
		}
		final Integer integer = myMethodToOccurrenceMap.get(method);
		return integer != null ? integer.intValue() : 0;
	}

	private int assignOccurrenceIndex(Method method)
	{
		if(method == null)
		{
			return 0;
		}
		final int count = getOccurrenceCount(method);
		myMethodToOccurrenceMap.put(method, count + 1);
		return count;
	}
}
