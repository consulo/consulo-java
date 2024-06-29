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
package com.intellij.java.impl.codeInspection.sameParameterValue;

import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionTool;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.analysis.codeInspection.reference.RefMethod;
import com.intellij.java.analysis.codeInspection.reference.RefParameter;
import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.impl.refactoring.util.InlineUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.inspection.reference.RefElement;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
@ExtensionImpl
public class SameParameterValueInspection extends GlobalJavaInspectionTool
{
	private static final Logger LOG = Logger.getInstance(SameParameterValueInspection.class);

	@Override
	@Nullable
	public CommonProblemDescriptor[] checkElement(
		@Nonnull RefEntity refEntity,
		@Nonnull AnalysisScope scope,
		@Nonnull InspectionManager manager,
		@Nonnull GlobalInspectionContext globalContext,
		@Nonnull ProblemDescriptionsProcessor processor,
		@Nonnull Object state
	)
	{
		ArrayList<ProblemDescriptor> problems = null;
		if (refEntity instanceof RefMethod refMethod)
		{
			if (refMethod.hasSuperMethods())
			{
				return null;
			}

			if (refMethod.isEntry())
			{
				return null;
			}

			RefParameter[] parameters = refMethod.getParameters();
			for (RefParameter refParameter : parameters)
			{
				String value = refParameter.getActualValueIfSame();
				if (value != null)
				{
					if (problems == null)
					{
						problems = new ArrayList<>(1);
					}
					final String paramName = refParameter.getName();
					problems.add(manager.createProblemDescriptor(
						refParameter.getElement(),
						InspectionLocalize.inspectionSameParameterProblemDescriptor(
							"<code>" + paramName + "</code>",
							"<code>" + value + "</code>"
						).get(),
						new InlineParameterValueFix(paramName, value),
						ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
						false
					));
				}
			}
		}

		return problems == null ? null : problems.toArray(new CommonProblemDescriptor[problems.size()]);
	}


	@Override
	protected boolean queryExternalUsagesRequests(
			final RefManager manager, final GlobalJavaInspectionContext globalContext,
			final ProblemDescriptionsProcessor processor, Object state)
	{
		manager.iterate(new RefJavaVisitor()
		{
			@Override
			public void visitElement(@Nonnull RefEntity refEntity)
			{
				if (refEntity instanceof RefElement && processor.getDescriptions(refEntity) != null)
				{
					refEntity.accept(new RefJavaVisitor()
					{
						@Override
						public void visitMethod(@Nonnull final RefMethod refMethod)
						{
							globalContext.enqueueMethodUsagesProcessor(refMethod, psiReference -> {
								processor.ignoreElement(refMethod);
								return false;
							});
						}
					});
				}
			}
		});

		return false;
	}

	@Override
	@Nonnull
	public String getDisplayName()
	{
		return InspectionLocalize.inspectionSameParameterDisplayName().get();
	}

	@Override
	@Nonnull
	public String getGroupDisplayName()
	{
		return InspectionLocalize.groupNamesDeclarationRedundancy().get();
	}

	@Override
	@Nonnull
	public String getShortName()
	{
		return "SameParameterValue";
	}

	@Override
	@Nullable
	public QuickFix getQuickFix(final String hint)
	{
		if (hint == null)
		{
			return null;
		}
		final int spaceIdx = hint.indexOf(' ');
		if (spaceIdx == -1 || spaceIdx >= hint.length() - 1)
		{
			return null; //invalid hint
		}
		final String paramName = hint.substring(0, spaceIdx);
		final String value = hint.substring(spaceIdx + 1);
		return new InlineParameterValueFix(paramName, value);
	}

	@Override
	@Nullable
	public String getHint(@Nonnull final QuickFix fix)
	{
		final InlineParameterValueFix valueFix = (InlineParameterValueFix) fix;
		return valueFix.getParamName() + " " + valueFix.getValue();
	}

	public static class InlineParameterValueFix implements LocalQuickFix
	{
		private final String myValue;
		private final String myParameterName;

		public InlineParameterValueFix(final String parameterName, final String value)
		{
			myValue = value;
			myParameterName = parameterName;
		}

		@Override
		@Nonnull
		public String getName()
		{
			return InspectionLocalize.inspectionSameParameterFixName(myParameterName, myValue).get();
		}

		@Override
		@Nonnull
		public String getFamilyName()
		{
			return getName();
		}

		@Override
		@RequiredWriteAction
		public void applyFix(@Nonnull final Project project, @Nonnull ProblemDescriptor descriptor)
		{
			final PsiElement element = descriptor.getPsiElement();
			final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
			LOG.assertTrue(method != null);
			PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class, false);
			if (parameter == null)
			{
				final PsiParameter[] parameters = method.getParameterList().getParameters();
				for (PsiParameter psiParameter : parameters)
				{
					if (Comparing.strEqual(psiParameter.getName(), myParameterName))
					{
						parameter = psiParameter;
						break;
					}
				}
			}
			if (parameter == null || !CommonRefactoringUtil.checkReadOnlyStatus(project, parameter)) {
				return;
			}

			final PsiExpression defToInline;
			try
			{
				defToInline = JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(myValue, parameter);
			}
			catch (IncorrectOperationException e)
			{
				return;
			}
			final PsiParameter parameterToInline = parameter;
			inlineSameParameterValue(method, parameterToInline, defToInline);
		}

		@RequiredUIAccess
		public static void inlineSameParameterValue(final PsiMethod method, final PsiParameter parameter, final PsiExpression defToInline)
		{
			final Collection<PsiReference> refsToInline = ReferencesSearch.search(parameter).findAll();

			method.getApplication().runWriteAction(() -> {
				try
				{
					PsiExpression[] exprs = new PsiExpression[refsToInline.size()];
					int idx = 0;
					for (PsiReference reference : refsToInline)
					{
						if (reference instanceof PsiJavaCodeReferenceElement javaCodeReferenceElement)
						{
							exprs[idx++] = InlineUtil.inlineVariable(parameter, defToInline, javaCodeReferenceElement);
						}
					}

					for (final PsiExpression expr : exprs)
					{
						if (expr != null)
						{
							InlineUtil.tryToInlineArrayCreationForVarargs(expr);
						}
					}
				}
				catch (IncorrectOperationException e)
				{
					LOG.error(e);
				}
			});

			removeParameter(method, parameter);
		}

		public static void removeParameter(final PsiMethod method, final PsiParameter parameter)
		{
			final PsiParameter[] parameters = method.getParameterList().getParameters();
			final List<ParameterInfoImpl> psiParameters = new ArrayList<>();
			int paramIdx = 0;
			final String paramName = parameter.getName();
			for (PsiParameter param : parameters)
			{
				if (!Comparing.strEqual(paramName, param.getName()))
				{
					psiParameters.add(new ParameterInfoImpl(paramIdx, param.getName(), param.getType()));
				}
				paramIdx++;
			}

			new ChangeSignatureProcessor(
				method.getProject(),
				method,
				false,
				null,
				method.getName(),
				method.getReturnType(),
				psiParameters.toArray(new ParameterInfoImpl[psiParameters.size()])
			).run();
		}

		public String getValue()
		{
			return myValue;
		}

		public String getParamName()
		{
			return myParameterName;
		}
	}
}
