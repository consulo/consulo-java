/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.typeMigration.ui;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import consulo.project.Project;
import consulo.ide.impl.idea.packageDependencies.ui.UsagesPanel;
import consulo.language.psi.PsiElement;
import com.intellij.java.impl.refactoring.typeMigration.TypeMigrationLabeler;
import com.intellij.java.impl.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import consulo.usage.UsageInfo;

/**
 * @author anna
 */
public class MigrationUsagesPanel extends UsagesPanel
{
	public MigrationUsagesPanel(Project project)
	{
		super(project);
	}

	@Override
	public String getInitialPositionText()
	{
		return "Select root to find reasons to migrate";
	}

	@Override
	public String getCodeUsagesString()
	{
		return "Found reasons to migrate";
	}

	public void showRootUsages(UsageInfo root, UsageInfo migration, final TypeMigrationLabeler labeler)
	{
		final PsiElement rootElement = root.getElement();
		if(rootElement == null)
		{
			return;
		}
		final Set<PsiElement> usages = labeler.getTypeUsages((TypeMigrationUsageInfo) migration, ((TypeMigrationUsageInfo) root));
		if(usages != null)
		{
			final List<UsageInfo> infos = new ArrayList<>(usages.size());
			for(PsiElement usage : usages)
			{
				if(usage != null && usage.isValid())
				{
					infos.add(new UsageInfo(usage));
				}
			}
			showUsages(new PsiElement[]{rootElement}, infos.toArray(UsageInfo.EMPTY_ARRAY));
		}
		else
		{
			showUsages(new PsiElement[]{rootElement}, new UsageInfo[]{migration});
		}
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(-1, 300);
	}
}