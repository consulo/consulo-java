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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.ide.impl.psi.util.PsiFormatUtilBase;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.java.analysis.impl.JavaQuickFixBundle;

public class StaticImportConstantFix extends StaticImportMemberFix<PsiField>
{
	private final SmartPsiElementPointer<PsiJavaCodeReferenceElement> myRef;

	public StaticImportConstantFix(@Nonnull PsiJavaCodeReferenceElement referenceElement)
	{
		myRef = SmartPointerManager.getInstance(referenceElement.getProject()).createSmartPsiElementPointer(referenceElement);
	}

	@Nonnull
	@Override
	protected String getBaseText()
	{
		return "Import static constant";
	}

	@Nonnull
	@Override
	protected String getMemberPresentableText(PsiField field)
	{
		return PsiFormatUtil.formatVariable(field, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS | PsiFormatUtilBase.SHOW_FQ_NAME, PsiSubstitutor.EMPTY);
	}

	@Nonnull
	@Override
	protected List<PsiField> getMembersToImport(boolean applicableOnly)
	{
		final Project project = myRef.getProject();
		PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
		final PsiJavaCodeReferenceElement element = myRef.getElement();
		String name = element != null ? element.getReferenceName() : null;
		if(name == null)
		{
			return Collections.emptyList();
		}
		if(element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression) element) || element.getParent() instanceof PsiTypeElement)
		{
			return Collections.emptyList();
		}
		final StaticMembersProcessor<PsiField> processor = new StaticMembersProcessor<PsiField>(element)
		{
			@Override
			protected boolean isApplicable(PsiField field, PsiElement place)
			{
				final PsiType expectedType = getExpectedType();
				return expectedType == null || TypeConversionUtil.isAssignable(expectedType, field.getType());
			}
		};
		cache.processFieldsWithName(name, processor, element.getResolveScope(), null);
		return processor.getMembersToImport(applicableOnly);
	}

	@Nonnull
	protected StaticImportMethodQuestionAction<PsiField> createQuestionAction(List<PsiField> methodsToImport, @Nonnull Project project, Editor editor)
	{
		return new StaticImportMethodQuestionAction<PsiField>(project, editor, methodsToImport, myRef)
		{
			@Nonnull
			@Override
			protected String getPopupTitle()
			{
				return JavaQuickFixBundle.message("field.to.import.chooser.title");
			}
		};
	}

	@Nullable
	@Override
	protected PsiElement getElement()
	{
		return myRef.getElement();
	}

	@Nullable
	@Override
	protected PsiElement getQualifierExpression()
	{
		final PsiJavaCodeReferenceElement element = myRef.getElement();
		return element != null ? element.getQualifier() : null;
	}

	@Nullable
	@Override
	protected PsiElement resolveRef()
	{
		final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement) getElement();
		return referenceElement != null ? referenceElement.advancedResolve(true).getElement() : null;
	}
}
