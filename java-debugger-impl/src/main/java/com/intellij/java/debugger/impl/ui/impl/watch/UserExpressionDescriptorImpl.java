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

/*
 * Class StaticDescriptorImpl
 * @author Jeka
 */
package com.intellij.java.debugger.impl.ui.impl.watch;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.engine.StackFrameContext;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.debugger.impl.DebuggerUtilsImpl;
import com.intellij.java.debugger.impl.ui.tree.UserExpressionDescriptor;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import com.intellij.java.language.psi.JavaCodeFragment;
import consulo.language.psi.PsiCodeFragment;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiType;
import consulo.internal.com.sun.jdi.Type;
import jakarta.annotation.Nullable;

public class UserExpressionDescriptorImpl extends EvaluationDescriptor implements UserExpressionDescriptor
{
	private final ValueDescriptorImpl myParentDescriptor;
	private final String myTypeName;
	private final String myName;
	private final int myEnumerationIndex;

	public UserExpressionDescriptorImpl(Project project, ValueDescriptorImpl parent, String typeName, String name, TextWithImports text, int enumerationIndex)
	{
		super(text, project);
		myParentDescriptor = parent;
		myTypeName = typeName;
		myName = name;
		myEnumerationIndex = enumerationIndex;
	}

	public String getName()
	{
		return StringUtil.isEmpty(myName) ? myText.getText() : myName;
	}

	@Nullable
	@Override
	public String getDeclaredType()
	{
		Type type = getType();
		return type != null ? type.name() : null;
	}

	protected PsiCodeFragment getEvaluationCode(final StackFrameContext context) throws EvaluateException
	{
		Pair<PsiElement, PsiType> psiClassAndType = DebuggerUtilsImpl.getPsiClassAndType(myTypeName, myProject);
		if(psiClassAndType.first == null)
		{
			throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.type.name", myTypeName));
		}
		PsiCodeFragment fragment = createCodeFragment(psiClassAndType.first);
		if(fragment instanceof JavaCodeFragment)
		{
			((JavaCodeFragment) fragment).setThisType(psiClassAndType.second);
		}
		return fragment;
	}

	public ValueDescriptorImpl getParentDescriptor()
	{
		return myParentDescriptor;
	}

	protected EvaluationContextImpl getEvaluationContext(final EvaluationContextImpl evaluationContext)
	{
		return evaluationContext.createEvaluationContext(myParentDescriptor.getValue());
	}

	public int getEnumerationIndex()
	{
		return myEnumerationIndex;
	}
}