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

import com.intellij.java.debugger.DebuggerContext;
import com.intellij.java.debugger.impl.engine.ContextUtil;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.ValueDescriptorImpl;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import consulo.internal.com.sun.jdi.ArrayReference;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.Value;

public class InstanceValueDescriptor extends ValueDescriptorImpl
{
	public InstanceValueDescriptor(Project project, Value value)
	{
		super(project, value);
	}

	@Override
	public String calcValueName()
	{
		ObjectReference ref = ((ObjectReference) getValue());
		if(ref instanceof ArrayReference)
		{
			ArrayReference arrayReference = (ArrayReference) ref;
			return NamesUtils.getArrayUniqueName(arrayReference);
		}
		return NamesUtils.getUniqueName(ref);
	}

	@Override
	public Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException
	{
		return getValue();
	}

	@Override
	public boolean isShowIdLabel()
	{
		return false;
	}

	@Override
	public PsiExpression getDescriptorEvaluation(DebuggerContext debuggerContext) throws EvaluateException
	{
		PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
		ObjectReference ref = ((ObjectReference) getValue());
		String name = NamesUtils.getUniqueName(ref).replace("@", "");
		String presentation = String.format("%s_DebugLabel", name);

		return elementFactory.createExpressionFromText(presentation, ContextUtil.getContextElement(debuggerContext));
	}
}
