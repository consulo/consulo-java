/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.classlayout;

import com.intellij.java.impl.ig.fixes.RenameFix;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.util.FileTypeUtils;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class ClassNameDiffersFromFileNameInspection extends BaseInspection
{

	@Override
	@Nonnull
	public String getDisplayName()
	{
		return InspectionGadgetsLocalize.classNameDiffersFromFileNameDisplayName().get();
	}

	@Override
	@Nonnull
	protected String buildErrorString(Object... infos)
	{
		return InspectionGadgetsLocalize.classNameDiffersFromFileNameProblemDescriptor().get();
	}

	@Override
	@Nullable
	protected InspectionGadgetsFix buildFix(Object... infos)
	{
		final PsiJavaFile file = (PsiJavaFile) infos[0];
		final String fileName = file.getName();
		final int prefixIndex = fileName.indexOf((int) '.');
		final String filenameWithoutPrefix = fileName.substring(0, prefixIndex);
		final PsiClass[] classes = file.getClasses();
		for(PsiClass psiClass : classes)
		{
			final String className = psiClass.getName();
			if(filenameWithoutPrefix.equals(className))
			{
				return null;
			}
		}
		return new RenameFix(filenameWithoutPrefix);
	}

	@Override
	public boolean shouldInspect(PsiFile file)
	{
		return !FileTypeUtils.isInServerPageFile(file);
	}

	@Override
	public BaseInspectionVisitor buildVisitor()
	{
		return new ClassNameDiffersFromFileNameVisitor();
	}

	private static class ClassNameDiffersFromFileNameVisitor extends BaseInspectionVisitor
	{

		@Override
		public void visitClass(@Nonnull PsiClass aClass)
		{
			final PsiElement parent = aClass.getParent();
			if(!(parent instanceof PsiJavaFile))
			{
				return;
			}
			final PsiJavaFile file = (PsiJavaFile) parent;
			final String className = aClass.getName();
			if(className == null)
			{
				return;
			}
			final String fileName = file.getName();
			final int prefixIndex = fileName.indexOf((int) '.');
			if(prefixIndex < 0)
			{
				return;
			}
			final String filenameWithoutPrefix = fileName.substring(0, prefixIndex);
			if(className.equals(filenameWithoutPrefix))
			{
				return;
			}
			registerClassError(aClass, file);
		}
	}
}