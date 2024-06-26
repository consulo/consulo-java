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
package com.intellij.java.impl.generate.config;

import jakarta.annotation.Nonnull;
import org.jetbrains.java.generate.psi.PsiAdapter;
import consulo.codeEditor.Editor;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;

/**
 * Inserts the method last in the javafile.
 */
public class InsertLastStrategy implements InsertNewMethodStrategy
{
	private static final InsertLastStrategy instance = new InsertLastStrategy();

	private InsertLastStrategy()
	{
	}

	public static InsertLastStrategy getInstance()
	{
		return instance;
	}

	@Override
	public PsiMethod insertNewMethod(PsiClass clazz, @Nonnull PsiMethod newMethod, Editor editor)
	{

		// if main method exists and is the last then add toString just before main method
		PsiMethod mainMethod = PsiAdapter.findPublicStaticVoidMainMethod(clazz);
		if(mainMethod != null)
		{
			// add before main method if it is the last method
			PsiMethod[] methods = clazz.getMethods();
			if(mainMethod.equals(methods[methods.length - 1]))
			{
				return (PsiMethod) clazz.addBefore(newMethod, mainMethod);  // return as the method is added
			}
		}

		// otherwise add it at the end
		PsiElement last = clazz.getRBrace(); // rbrace is the last } java token. fixes bug #9
		return (PsiMethod) clazz.addBefore(newMethod, last);
	}

	public String toString()
	{
		return "Last";
	}
}
