/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
 * User: anna
 * Date: 19-Dec-2007
 */
package com.intellij.java.impl.codeInspection;

import com.intellij.java.analysis.codeInspection.BatchSuppressManager;
import com.intellij.java.analysis.codeInspection.GlobalJavaInspectionContext;
import com.intellij.java.language.JavaLanguage;
import consulo.language.Language;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class GlobalJavaBatchInspectionTool extends GlobalInspectionTool implements BatchSuppressableTool
{
	@Override
	public boolean queryExternalUsagesRequests(@Nonnull final InspectionManager manager,
											   @Nonnull final GlobalInspectionContext globalContext,
											   @Nonnull final ProblemDescriptionsProcessor problemDescriptionsProcessor,
											   Object state)
	{
		return queryExternalUsagesRequests(globalContext.getRefManager(), globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT), problemDescriptionsProcessor);
	}

	protected boolean queryExternalUsagesRequests(@Nonnull RefManager manager, @Nonnull GlobalJavaInspectionContext globalContext, @Nonnull ProblemDescriptionsProcessor processor)
	{
		return false;
	}

	@Nonnull
	@Override
	public SuppressQuickFix[] getBatchSuppressActions(@Nullable PsiElement element)
	{
		return BatchSuppressManager.getInstance().createBatchSuppressActions(HighlightDisplayKey.find(getShortName()));
	}

	@Override
	public boolean isSuppressedFor(@Nonnull final PsiElement element)
	{
		return BatchSuppressManager.getInstance().isSuppressedFor(element, getShortName());
	}

	@Nonnull
	@Override
	public HighlightDisplayLevel getDefaultLevel()
	{
		return HighlightDisplayLevel.WARNING;
	}

	@Override
	public boolean isEnabledByDefault()
	{
		return true;
	}

	@Nullable
	@Override
	public Language getLanguage()
	{
		return JavaLanguage.INSTANCE;
	}
}
