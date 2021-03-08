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
package com.intellij.debugger.memory.utils;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ErrorsValueGroup extends XValueGroup
{
	private final Map<String, List<XNamedValue>> myErrorMessage2ValueMap = new HashMap<>();

	public ErrorsValueGroup()
	{
		super("Errors");
	}

	public void addErrorValue(@Nonnull String message, @Nonnull XNamedValue value)
	{
		List<XNamedValue> lst;
		if(!myErrorMessage2ValueMap.containsKey(message))
		{
			myErrorMessage2ValueMap.put(message, new ArrayList<>());
		}

		lst = myErrorMessage2ValueMap.get(message);
		lst.add(value);
	}

	public boolean isEmpty()
	{
		return myErrorMessage2ValueMap.isEmpty();
	}

	@Nullable
	@Override
	public Image getIcon()
	{
		return AllIcons.General.Error;
	}

	@Override
	public void computeChildren(@Nonnull XCompositeNode node)
	{
		XValueChildrenList lst = new XValueChildrenList();
		myErrorMessage2ValueMap.keySet().forEach(s -> lst.addTopGroup(new MyErrorsValueGroup(s)));
		node.addChildren(lst, true);
	}

	private class MyErrorsValueGroup extends XValueGroup
	{

		@Override
		public void computeChildren(@Nonnull XCompositeNode node)
		{
			XValueChildrenList lst = new XValueChildrenList();
			String name = getName();
			myErrorMessage2ValueMap.get(name).forEach(lst::add);
			node.addChildren(lst, true);
		}

		MyErrorsValueGroup(@Nonnull String name)
		{
			super(name);
		}
	}
}
