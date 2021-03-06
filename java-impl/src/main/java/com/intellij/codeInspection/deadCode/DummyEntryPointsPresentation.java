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
package com.intellij.codeInspection.deadCode;

import javax.annotation.Nonnull;

import com.intellij.codeInspection.GlobalJavaInspectionContext;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.HTMLComposerImpl;
import com.intellij.codeInspection.ex.InspectionRVContentProvider;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.QuickFixAction;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionNode;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.util.RefFilter;

public class DummyEntryPointsPresentation extends UnusedDeclarationPresentation
{
	private static final RefEntryPointFilter myFilter = new RefEntryPointFilter();
	private QuickFixAction[] myQuickFixActions;

	public DummyEntryPointsPresentation(@Nonnull InspectionToolWrapper toolWrapper,
			@Nonnull GlobalInspectionContextImpl context)
	{
		super(toolWrapper, context);
	}

	@Override
	public RefFilter getFilter()
	{
		return myFilter;
	}

	@Override
	public QuickFixAction[] getQuickFixes(@Nonnull final RefEntity[] refElements)
	{
		if(myQuickFixActions == null)
		{
			myQuickFixActions = new QuickFixAction[]{new MoveEntriesToSuspicious(getToolWrapper())};
		}
		return myQuickFixActions;
	}

	@Override
	protected String getSeverityDelegateName()
	{
		return UnusedDeclarationInspection.SHORT_NAME;
	}

	private class MoveEntriesToSuspicious extends QuickFixAction
	{
		private MoveEntriesToSuspicious(@Nonnull InspectionToolWrapper toolWrapper)
		{
			super(InspectionsBundle.message("inspection.dead.code.remove.from.entry.point.quickfix"), null, null,
					toolWrapper);
		}

		@Override
		protected boolean applyFix(@Nonnull RefEntity[] refElements)
		{
			final EntryPointsManager entryPointsManager = getContext().getExtension(GlobalJavaInspectionContext
					.CONTEXT).getEntryPointsManager(getContext().getRefManager());
			for(RefEntity refElement : refElements)
			{
				if(refElement instanceof RefElement)
				{
					entryPointsManager.removeEntryPoint((RefElement) refElement);
				}
			}

			return true;
		}
	}

	@Nonnull
	@Override
	public InspectionNode createToolNode(@Nonnull GlobalInspectionContextImpl context,
			@Nonnull InspectionNode node,
			@Nonnull InspectionRVContentProvider provider,
			@Nonnull InspectionTreeNode parentNode,
			boolean showStructure)
	{
		return node;
	}

	@Override
	@Nonnull
	public HTMLComposerImpl getComposer()
	{
		return new DeadHTMLComposer(this);
	}
}
