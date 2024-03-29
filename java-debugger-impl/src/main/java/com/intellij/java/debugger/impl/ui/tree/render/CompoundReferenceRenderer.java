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
package com.intellij.java.debugger.impl.ui.tree.render;

import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.Type;

public class CompoundReferenceRenderer extends CompoundTypeRenderer
{
	public CompoundReferenceRenderer(NodeRendererSettings rendererSettings, String name, ValueLabelRenderer labelRenderer, ChildrenRenderer childrenRenderer)
	{
		super(rendererSettings, name, labelRenderer, childrenRenderer);
	}

	public boolean isApplicable(Type type)
	{
		if(type instanceof ReferenceType)
		{
			return super.isApplicable(type);
		}
		return false;
	}
}
