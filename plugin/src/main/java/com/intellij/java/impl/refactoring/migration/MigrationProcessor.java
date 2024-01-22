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
package com.intellij.java.impl.refactoring.migration;

import com.intellij.java.impl.psi.impl.migration.PsiMigrationManager;
import com.intellij.java.language.psi.PsiMigration;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.content.scope.SearchScope;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.localHistory.LocalHistory;
import consulo.localHistory.LocalHistoryAction;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;

/**
 * @author ven
 */
public class MigrationProcessor extends BaseRefactoringProcessor
{
	private final MigrationMap myMigrationMap;
	private static final String REFACTORING_NAME = RefactoringBundle.message("migration.title");
	private PsiMigration myPsiMigration;
	private final SearchScope mySearchScope;
	private ArrayList<SmartPsiElementPointer<PsiElement>> myRefsToShorten;

	public MigrationProcessor(Project project, MigrationMap migrationMap)
	{
		this(project, migrationMap, GlobalSearchScope.projectScope(project));
	}

	public MigrationProcessor(Project project, MigrationMap migrationMap, SearchScope scope)
	{
		super(project);
		myMigrationMap = migrationMap;
		mySearchScope = scope;
		myPsiMigration = startMigration(project);
	}

	@Override
	@Nonnull
	protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages)
	{
		return new MigrationUsagesViewDescriptor(myMigrationMap, false);
	}

	private PsiMigration startMigration(Project project)
	{
		final PsiMigration migration = PsiMigrationManager.getInstance(project).startMigration();
		findOrCreateEntries(project, migration);
		return migration;
	}

	private void findOrCreateEntries(Project project, final PsiMigration migration)
	{
		for(int i = 0; i < myMigrationMap.getEntryCount(); i++)
		{
			MigrationMapEntry entry = myMigrationMap.getEntryAt(i);
			if(entry.getType() == MigrationMapEntry.PACKAGE)
			{
				MigrationUtil.findOrCreatePackage(project, migration, entry.getOldName());
			}
			else
			{
				MigrationUtil.findOrCreateClass(project, migration, entry.getOldName());
			}
		}
	}

	@Override
	protected void refreshElements(@jakarta.annotation.Nonnull PsiElement[] elements)
	{
		myPsiMigration = startMigration(myProject);
	}

	@Override
	@jakarta.annotation.Nonnull
	protected UsageInfo[] findUsages()
	{
		ArrayList<UsageInfo> usagesVector = new ArrayList<>();
		try
		{
			if(myMigrationMap == null)
			{
				return UsageInfo.EMPTY_ARRAY;
			}
			for(int i = 0; i < myMigrationMap.getEntryCount(); i++)
			{
				MigrationMapEntry entry = myMigrationMap.getEntryAt(i);
				UsageInfo[] usages;
				if(entry.getType() == MigrationMapEntry.PACKAGE)
				{
					usages = MigrationUtil.findPackageUsages(myProject, myPsiMigration, entry.getOldName(), mySearchScope);
				}
				else
				{
					usages = MigrationUtil.findClassUsages(myProject, myPsiMigration, entry.getOldName(), mySearchScope);
				}

				for(UsageInfo usage : usages)
				{
					usagesVector.add(new MigrationUsageInfo(usage, entry));
				}
			}
		}
		finally
		{
			//invalidating resolve caches without write action could lead to situations when somebody with read action resolves reference and gets ResolveResult
			//then here, in another read actions, all caches are invalidated but those resolve result is used without additional checks inside that read action - but it's already invalid
			ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(this::finishFindMigration), myProject.getDisposed());
		}
		return usagesVector.toArray(UsageInfo.EMPTY_ARRAY);
	}

	private void finishFindMigration()
	{
		if(myPsiMigration != null)
		{
			myPsiMigration.finish();
			myPsiMigration = null;
		}
	}

	@Override
	protected boolean preprocessUsages(@Nonnull Ref<UsageInfo[]> refUsages)
	{
		if(refUsages.get().length == 0)
		{
			Messages.showInfoMessage(myProject, RefactoringBundle.message("migration.no.usages.found.in.the.project"), REFACTORING_NAME);
			return false;
		}
		setPreviewUsages(true);
		return true;
	}

	@Override
	protected void performRefactoring(@jakarta.annotation.Nonnull UsageInfo[] usages)
	{
		finishFindMigration();
		final PsiMigration psiMigration = PsiMigrationManager.getInstance(myProject).startMigration();
		LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName());

		myRefsToShorten = new ArrayList<>();
		try
		{
			boolean sameShortNames = false;
			for(int i = 0; i < myMigrationMap.getEntryCount(); i++)
			{
				MigrationMapEntry entry = myMigrationMap.getEntryAt(i);
				String newName = entry.getNewName();
				PsiElement element = entry.getType() == MigrationMapEntry.PACKAGE ? MigrationUtil.findOrCreatePackage(myProject, psiMigration, newName) : MigrationUtil.findOrCreateClass(myProject,
						psiMigration, newName);
				MigrationUtil.doMigration(element, newName, usages, myRefsToShorten);
				if(!sameShortNames && Comparing.strEqual(StringUtil.getShortName(entry.getOldName()), StringUtil.getShortName(entry.getNewName())))
				{
					sameShortNames = true;
				}
			}

			if(!sameShortNames)
			{
				myRefsToShorten.clear();
			}
		}
		finally
		{
			a.finish();
			psiMigration.finish();
		}
	}

	@Override
	protected void performPsiSpoilingRefactoring()
	{
		JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myProject);
		for(SmartPsiElementPointer<PsiElement> pointer : myRefsToShorten)
		{
			PsiElement element = pointer.getElement();
			if(element != null)
			{
				styleManager.shortenClassReferences(element);
			}
		}
	}

	@Override
	@jakarta.annotation.Nonnull
	protected String getCommandName()
	{
		return REFACTORING_NAME;
	}

	static class MigrationUsageInfo extends UsageInfo
	{
		MigrationMapEntry mapEntry;

		MigrationUsageInfo(UsageInfo info, MigrationMapEntry mapEntry)
		{
			super(info.getElement(), info.getRangeInElement().getStartOffset(), info.getRangeInElement().getEndOffset());
			this.mapEntry = mapEntry;
		}
	}
}
