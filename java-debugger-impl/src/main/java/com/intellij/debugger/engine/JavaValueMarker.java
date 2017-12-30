/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import org.jetbrains.annotations.NotNull;
import com.intellij.xdebugger.frame.XValueMarkerProvider;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.Value;

/**
 * @author egor
 */
public class JavaValueMarker extends XValueMarkerProvider<JavaValue, Long>
{
	public JavaValueMarker()
	{
		super(JavaValue.class);
	}

	@Override
	public boolean canMark(@NotNull JavaValue value)
	{
		Value obj = value.getDescriptor().getValue();
		return obj instanceof ObjectReference;
	}

	@Override
	public Long getMarker(@NotNull JavaValue value)
	{
		Value obj = value.getDescriptor().getValue();
		if(obj instanceof ObjectReference)
		{
			return ((ObjectReference) obj).uniqueID();
		}
		return null;
	}
}