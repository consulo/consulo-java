/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.typeMigration;

import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.usage.UsageViewBundle;
import consulo.usage.UsageViewDescriptor;
import jakarta.annotation.Nonnull;

class TypeMigrationViewDescriptor implements UsageViewDescriptor
{
	private final PsiElement myElement;

	public TypeMigrationViewDescriptor(PsiElement elements)
	{
		myElement = elements;
	}

	@Override
	@Nonnull
	public PsiElement[] getElements()
	{
		return new PsiElement[]{myElement};
	}

	@Override
	public String getProcessedElementsHeader()
	{
		return "Root for type migration";
	}

	@Override
	public String getCodeReferencesText(int usagesCount, int filesCount)
	{
		return RefactoringLocalize.occurencesToBeMigrated(UsageViewBundle.getReferencesString(usagesCount, filesCount)).get();
	}

	@Override
	public String getCommentReferencesText(int usagesCount, int filesCount)
	{
		return null;
	}
}
