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

/*
 * Class WatchItemDescriptor
 * @author Jeka
 */
package com.intellij.java.debugger.impl.ui.impl.watch;

import jakarta.annotation.Nullable;
import com.intellij.java.debugger.engine.StackFrameContext;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.debugger.impl.PositionUtil;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.language.psi.PsiCodeFragment;
import consulo.internal.com.sun.jdi.Value;

/**
 * update(Value, boolean) method must be called whenever the state of the target VM changes
 */
public class WatchItemDescriptor extends EvaluationDescriptor
{

	@Nullable
	private final String myCustomName;

	public WatchItemDescriptor(Project project, TextWithImports text)
	{
		this(project, text, (String) null);
	}

	public WatchItemDescriptor(Project project, TextWithImports text, @Nullable String customName)
	{
		super(text, project);
		myCustomName = customName;
		setValueLabel("");
	}

	public WatchItemDescriptor(Project project, TextWithImports text, Value value)
	{
		this(project, text, value, null);
	}

	public WatchItemDescriptor(Project project, TextWithImports text, Value value, @Nullable String customName)
	{
		super(text, project, value);
		myCustomName = customName;
		setValueLabel("");
	}

	@Override
	public String getName()
	{
		final String customName = myCustomName;
		return customName == null ? getEvaluationText().getText() : customName;
	}

	public void setNew()
	{
		myIsNew = true;
	}

	// call update() after setting a new expression
	public void setEvaluationText(TextWithImports evaluationText)
	{
		if(!Comparing.equal(getEvaluationText(), evaluationText))
		{
			setLvalue(false);
		}
		myText = evaluationText;
		myIsNew = true;
		setValueLabel("");
	}

	@Override
	protected EvaluationContextImpl getEvaluationContext(EvaluationContextImpl evaluationContext)
	{
		return evaluationContext;
	}

	@Override
	protected PsiCodeFragment getEvaluationCode(StackFrameContext context) throws EvaluateException
	{
		return createCodeFragment(PositionUtil.getContextElement(context));
	}
}