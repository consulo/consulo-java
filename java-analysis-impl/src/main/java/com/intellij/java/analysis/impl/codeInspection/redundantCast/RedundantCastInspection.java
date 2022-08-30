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
package com.intellij.java.analysis.impl.codeInspection.redundantCast;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.java.analysis.impl.codeInspection.miscGenerics.GenericsInspectionToolBase;
import com.intellij.java.analysis.impl.codeInspection.miscGenerics.SuspiciousMethodCallUtil;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.RedundantCastUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class RedundantCastInspection extends GenericsInspectionToolBase
{
	private final LocalQuickFix myQuickFixAction;
	private static final String DISPLAY_NAME = InspectionsBundle.message("inspection.redundant.cast.display.name");
	@NonNls
	private static final String SHORT_NAME = "RedundantCast";

	public boolean IGNORE_SUSPICIOUS_METHOD_CALLS;


	public RedundantCastInspection()
	{
		myQuickFixAction = new AcceptSuggested();
	}

	@Override
	@Nullable
	public ProblemDescriptor[] getDescriptions(@Nonnull PsiElement where, @Nonnull InspectionManager manager, boolean isOnTheFly)
	{
		List<PsiTypeCastExpression> redundantCasts = RedundantCastUtil.getRedundantCastsInside(where);
		if(redundantCasts.isEmpty())
		{
			return null;
		}
		List<ProblemDescriptor> descriptions = new ArrayList<>(redundantCasts.size());
		for(PsiTypeCastExpression redundantCast : redundantCasts)
		{
			ProblemDescriptor descriptor = createDescription(redundantCast, manager, isOnTheFly);
			if(descriptor != null)
			{
				descriptions.add(descriptor);
			}
		}
		if(descriptions.isEmpty())
		{
			return null;
		}
		return descriptions.toArray(ProblemDescriptor.EMPTY_ARRAY);
	}

	@Override
	public ProblemDescriptor[] checkField(@Nonnull PsiField field, @Nonnull InspectionManager manager, boolean isOnTheFly)
	{
		return getDescriptions(field, manager, isOnTheFly);
	}

	@Override
	public void writeSettings(@Nonnull Element node) throws WriteExternalException
	{
		if(IGNORE_SUSPICIOUS_METHOD_CALLS)
		{
			super.writeSettings(node);
		}
	}

	@Override
	public JComponent createOptionsPanel()
	{
		final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
		optionsPanel.addCheckbox("Ignore casts in suspicious collections method calls", "IGNORE_SUSPICIOUS_METHOD_CALLS");
		return optionsPanel;
	}

	@Nullable
	private ProblemDescriptor createDescription(@Nonnull PsiTypeCastExpression cast, @Nonnull InspectionManager manager, boolean onTheFly)
	{
		PsiExpression operand = cast.getOperand();
		PsiTypeElement castType = cast.getCastType();
		if(operand == null || castType == null)
		{
			return null;
		}
		PsiElement parent = PsiUtil.skipParenthesizedExprUp(cast.getParent());
		if(parent instanceof PsiExpressionList)
		{
			final PsiElement gParent = parent.getParent();
			if(gParent instanceof PsiMethodCallExpression && IGNORE_SUSPICIOUS_METHOD_CALLS)
			{
				final String message = SuspiciousMethodCallUtil
						.getSuspiciousMethodCallMessage((PsiMethodCallExpression) gParent, operand, operand.getType(), true, new ArrayList<>(), 0);
				if(message != null)
				{
					return null;
				}
			}
		}

		String message = InspectionsBundle.message("inspection.redundant.cast.problem.descriptor",
				"<code>" + PsiExpressionTrimRenderer.render(operand) + "</code>", "<code>#ref</code> #loc");
		return manager.createProblemDescriptor(castType, message, myQuickFixAction, ProblemHighlightType.LIKE_UNUSED_SYMBOL, onTheFly);
	}


	private static class AcceptSuggested implements LocalQuickFix
	{
		@Override
		@Nonnull
		public String getFamilyName()
		{
			return InspectionsBundle.message("inspection.redundant.cast.remove.quickfix");
		}

		@Override
		public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
		{
			PsiElement castTypeElement = descriptor.getPsiElement();
			PsiTypeCastExpression cast = castTypeElement == null ? null : (PsiTypeCastExpression) castTypeElement.getParent();
			if(cast != null)
			{
				RemoveRedundantCastUtil.removeCast(cast);
			}
		}
	}

	@Override
	@Nonnull
	public String getDisplayName()
	{
		return DISPLAY_NAME;
	}

	@Override
	@Nonnull
	public String getGroupDisplayName()
	{
		return GroupNames.VERBOSE_GROUP_NAME;
	}

	@Override
	@Nonnull
	public String getShortName()
	{
		return SHORT_NAME;
	}
}
