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
package com.intellij.java.debugger.impl.ui.tree;

import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import consulo.execution.debug.ui.ValueMarkup;
import consulo.internal.com.sun.jdi.Type;
import consulo.internal.com.sun.jdi.Value;
import consulo.language.psi.PsiElement;
import consulo.ui.image.Image;

import jakarta.annotation.Nullable;

public interface ValueDescriptor extends NodeDescriptor
{
	PsiElement getDescriptorEvaluation(DebuggerContext context) throws EvaluateException;

	Value getValue();

	@Nullable
	default Type getType()
	{
		Value value = getValue();
		return value != null ? value.type() : null;
	}

	void setValueLabel(String label);

	String setValueLabelFailed(EvaluateException e);

	Image setValueIcon(Image icon);

	boolean isArray();

	boolean isLvalue();

	boolean isNull();

	boolean isPrimitive();

	boolean isString();

	@Nullable
	ValueMarkup getMarkup(final DebugProcess debugProcess);

	void setMarkup(final DebugProcess debugProcess, @Nullable ValueMarkup markup);
}
