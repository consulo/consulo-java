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
package com.intellij.java.debugger.impl.memory.component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Nonnull;
import com.intellij.java.debugger.impl.memory.utils.StackFrameItem;
import consulo.util.dataholder.Key;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.ReferenceType;
import jakarta.annotation.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public class MemoryViewDebugProcessData
{
	public static final Key<MemoryViewDebugProcessData> KEY = Key.create("MemoryView.DebugProcessData");

	private final TrackedStacksContainer myStacksContainer = new MyStackContainer();

	@Nonnull
	public TrackedStacksContainer getTrackedStacks()
	{
		return myStacksContainer;
	}

	private static class MyStackContainer implements TrackedStacksContainer
	{
		private final Map<ReferenceType, Map<ObjectReference, List<StackFrameItem>>> myType2Reference2Stack = new ConcurrentHashMap<>();

		private final Map<ReferenceType, Map<ObjectReference, List<StackFrameItem>>> myPinnedType2Reference2Stack = new ConcurrentHashMap<>();

		@Nullable
		@Override
		public List<StackFrameItem> getStack(@Nonnull ObjectReference reference)
		{
			final List<StackFrameItem> stack = extract(myType2Reference2Stack, reference);
			return stack != null ? stack : extract(myPinnedType2Reference2Stack, reference);
		}

		@Override
		public void addStack(@Nonnull ObjectReference ref, @Nonnull List<StackFrameItem> frames)
		{
			myType2Reference2Stack.computeIfAbsent(ref.referenceType(), referenceType -> new ConcurrentHashMap<>()).put(ref, frames);
		}

		@Override
		public void pinStacks(@Nonnull ReferenceType referenceType)
		{
			final Map<ObjectReference, List<StackFrameItem>> ref2Stack = myType2Reference2Stack.get(referenceType);
			if(ref2Stack != null)
			{
				myPinnedType2Reference2Stack.put(referenceType, ref2Stack);
			}
		}

		@Override
		public void unpinStacks(@Nonnull ReferenceType referenceType)
		{
			myPinnedType2Reference2Stack.remove(referenceType);
		}

		@Override
		public void release()
		{
			myType2Reference2Stack.clear();
		}

		@Override
		public void clear()
		{
			release();
			myPinnedType2Reference2Stack.clear();
		}

		@Nullable
		private static List<StackFrameItem> extract(@Nonnull Map<ReferenceType, Map<ObjectReference, List<StackFrameItem>>> map, @Nonnull ObjectReference ref)
		{
			final Map<ObjectReference, List<StackFrameItem>> ref2Stack = map.get(ref.referenceType());
			return ref2Stack != null ? ref2Stack.get(ref) : null;
		}
	}
}
