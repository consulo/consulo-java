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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.VisibilityUtil;
import com.siyeh.ig.InspectionGadgetsFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.usage.UsageInfo;

import javax.annotation.Nonnull;

/**
 * @author Bas Leijdekkers
 */
class MakePublicStaticVoidFix extends InspectionGadgetsFix
{
	private final String myName;
	private final boolean myMakeStatic;
	private final String myNewVisibility;

	public MakePublicStaticVoidFix(PsiMethod method, boolean makeStatic)
	{
		this(method, makeStatic, PsiModifier.PUBLIC);
	}

	public MakePublicStaticVoidFix(PsiMethod method, boolean makeStatic, @PsiModifier.ModifierConstant String newVisibility)
	{
		String presentableVisibility = VisibilityUtil.getVisibilityString(newVisibility);
		myName = "Change signature of \'" + PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_MODIFIERS | PsiFormatUtilBase
				.SHOW_PARAMETERS | PsiFormatUtilBase.SHOW_TYPE, PsiFormatUtilBase.SHOW_TYPE) + "\' to \'" + (presentableVisibility.isEmpty() ? "" : presentableVisibility + " ") + (makeStatic ?
				"static " : "") + "void " + method.getName() + "()\'";
		myMakeStatic = makeStatic;
		myNewVisibility = newVisibility;
	}

	@Override
	protected void doFix(final Project project, ProblemDescriptor descriptor)
	{
		final PsiMethod method = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
		if(method != null)
		{
			ChangeSignatureProcessor csp = new ChangeSignatureProcessor(project, method, false, myNewVisibility, method.getName(), PsiType.VOID, new ParameterInfoImpl[0])
			{
				@Override
				protected void performRefactoring(@Nonnull UsageInfo[] usages)
				{
					super.performRefactoring(usages);
					PsiUtil.setModifierProperty(method, PsiModifier.STATIC, myMakeStatic);
				}
			};
			csp.run();
		}
	}

	@Override
	public boolean startInWriteAction()
	{
		return false;
	}

	@Nonnull
	@Override
	public String getFamilyName()
	{
		return "Fix modifiers";
	}

	@Override
	@Nonnull
	public String getName()
	{
		return myName;
	}
}
