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
package com.intellij.java.debugger.impl.engine;

import javax.annotation.Nonnull;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

public abstract class SourcePositionProvider
{
	public static final ExtensionPointName<SourcePositionProvider> EP_NAME = ExtensionPointName.create("consulo.java.debugger.sourcePositionProvider");

	@javax.annotation.Nullable
	public static SourcePosition getSourcePosition(@Nonnull NodeDescriptor descriptor, @Nonnull Project project, @Nonnull DebuggerContextImpl context)
	{
		return getSourcePosition(descriptor, project, context, false);
	}

	@javax.annotation.Nullable
	public static SourcePosition getSourcePosition(@Nonnull NodeDescriptor descriptor, @Nonnull Project project, @Nonnull DebuggerContextImpl context, boolean nearest)
	{
		for(SourcePositionProvider provider : EP_NAME.getExtensions())
		{
			SourcePosition sourcePosition = provider.computeSourcePosition(descriptor, project, context, nearest);
			if(sourcePosition != null)
			{
				return sourcePosition;
			}
		}
		return null;
	}

	@javax.annotation.Nullable
	protected abstract SourcePosition computeSourcePosition(@Nonnull NodeDescriptor descriptor, @Nonnull Project project, @Nonnull DebuggerContextImpl context, boolean nearest);
}
