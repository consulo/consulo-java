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

import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.usage.UsageViewBundle;
import consulo.usage.UsageViewDescriptor;
import jakarta.annotation.Nonnull;

class MigrationUsagesViewDescriptor implements UsageViewDescriptor
{
	private final boolean isSearchInComments;
	private final MigrationMap myMigrationMap;

	public MigrationUsagesViewDescriptor(MigrationMap migrationMap, boolean isSearchInComments)
	{
		myMigrationMap = migrationMap;
		this.isSearchInComments = isSearchInComments;
	}

	public MigrationMap getMigrationMap()
	{
		return myMigrationMap;
	}

	@Override
	@Nonnull
	public PsiElement[] getElements()
	{
		return PsiElement.EMPTY_ARRAY;
	}

	@Override
	public String getProcessedElementsHeader()
	{
		return null;
	}

	@Override
	public String getCodeReferencesText(int usagesCount, int filesCount)
	{
		return RefactoringLocalize.referencesInCodeToElementsFromMigrationMap(
			myMigrationMap.getName(),
			UsageViewBundle.getReferencesString(usagesCount, filesCount)
		).get();
	}

	@Override
	public String getCommentReferencesText(int usagesCount, int filesCount)
	{
		return null;
	}

	public String getInfo()
	{
		return RefactoringLocalize.pressTheDoMigrateButton(myMigrationMap.getName()).get();
	}
}
