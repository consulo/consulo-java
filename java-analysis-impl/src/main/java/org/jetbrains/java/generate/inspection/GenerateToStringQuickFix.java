/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.inspection;

import javax.annotation.Nonnull;

import org.jetbrains.java.generate.GenerateToStringActionHandler;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Quick fix to run Generate toString() to fix any code inspection problems.
 */
public class GenerateToStringQuickFix implements LocalQuickFix
{

	public static final GenerateToStringQuickFix INSTANCE = new GenerateToStringQuickFix();

	private GenerateToStringQuickFix()
	{
	}

	public static GenerateToStringQuickFix getInstance()
	{
		return INSTANCE;
	}

	@Override
	@Nonnull
	public String getName()
	{
		return "Generate toString()";
	}

	@Override
	@Nonnull
	public String getFamilyName()
	{
		return "Generate";
	}

	@Override
	public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor desc)
	{
		final PsiClass clazz = PsiTreeUtil.getParentOfType(desc.getPsiElement(), PsiClass.class);
		if(clazz == null)
		{
			return; // no class to fix
		}
		GenerateToStringActionHandler handler = ServiceManager.getService(GenerateToStringActionHandler.class);
		handler.executeActionQuickFix(project, clazz);
	}
}
