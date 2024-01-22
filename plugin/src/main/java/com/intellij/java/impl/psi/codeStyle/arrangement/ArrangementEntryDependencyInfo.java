/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.codeStyle.arrangement;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Nonnull;

/**
 * @author Denis Zhdanov
 * @since 9/19/12 6:41 PM
 */
public class ArrangementEntryDependencyInfo
{

	@jakarta.annotation.Nonnull
	private final List<ArrangementEntryDependencyInfo> myDependentEntries = new ArrayList<ArrangementEntryDependencyInfo>();

	@Nonnull
	private final JavaElementArrangementEntry myAnchorEntry;

	public ArrangementEntryDependencyInfo(@Nonnull JavaElementArrangementEntry entry)
	{
		myAnchorEntry = entry;
	}

	public void addDependentEntryInfo(@jakarta.annotation.Nonnull ArrangementEntryDependencyInfo info)
	{
		myDependentEntries.add(info);
	}

	@jakarta.annotation.Nonnull
	public List<ArrangementEntryDependencyInfo> getDependentEntriesInfos()
	{
		return myDependentEntries;
	}

	@jakarta.annotation.Nonnull
	public JavaElementArrangementEntry getAnchorEntry()
	{
		return myAnchorEntry;
	}

	@Override
	public String toString()
	{
		return myAnchorEntry.toString();
	}
}
