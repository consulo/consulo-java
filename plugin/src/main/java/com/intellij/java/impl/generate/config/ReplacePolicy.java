/*
 * Copyright 2001-2007 the original author or authors.
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
package com.intellij.java.impl.generate.config;

import javax.annotation.Nonnull;
import consulo.codeEditor.Editor;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.util.IncorrectOperationException;

/**
 * This policy is to replace the existing <code>toString</code> method.
 */
public class ReplacePolicy implements ConflictResolutionPolicy
{

	private static final ReplacePolicy instance = new ReplacePolicy();

	private ReplacePolicy()
	{
	}

	public static ReplacePolicy getInstance()
	{
		return instance;
	}

	@Override
	public void setNewMethodStrategy(InsertNewMethodStrategy strategy)
	{
		DuplicatePolicy.getInstance().setNewMethodStrategy(strategy);
	}

	@Override
	public PsiMethod applyMethod(PsiClass clazz, PsiMethod existingMethod, @Nonnull PsiMethod newMethod, Editor editor) throws IncorrectOperationException
	{
		if(existingMethod != null)
		{
			return (PsiMethod) existingMethod.replace(newMethod);
		}
		else
		{
			return DuplicatePolicy.getInstance().applyMethod(clazz, existingMethod, newMethod, editor);
		}
	}

	public String toString()
	{
		return "Replace existing";
	}

}
