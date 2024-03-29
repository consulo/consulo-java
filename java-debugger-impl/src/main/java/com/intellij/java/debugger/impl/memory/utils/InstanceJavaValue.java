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
package com.intellij.java.debugger.impl.memory.utils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeManagerImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import consulo.internal.com.sun.jdi.ObjectReference;

public class InstanceJavaValue extends JavaValue
{
	public InstanceJavaValue(@Nonnull ValueDescriptorImpl valueDescriptor, @Nonnull EvaluationContextImpl evaluationContext, NodeManagerImpl nodeManager)
	{
		super(null, valueDescriptor, evaluationContext, nodeManager, false);
	}

	@Nullable
	@Override
	public String getEvaluationExpression()
	{
		ObjectReference ref = ((ObjectReference) getDescriptor().getValue());
		return NamesUtils.getUniqueName(ref);
	}

	@Override
	public boolean canNavigateToSource()
	{
		return false;
	}
}
