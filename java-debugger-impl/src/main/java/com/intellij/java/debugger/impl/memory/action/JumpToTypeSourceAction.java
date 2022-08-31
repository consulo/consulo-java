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
package com.intellij.java.debugger.impl.memory.action;

import java.util.List;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import consulo.internal.com.sun.jdi.ArrayType;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.VirtualMachine;

public class JumpToTypeSourceAction extends ClassesActionBase
{

	@Override
	protected boolean isEnabled(AnActionEvent e)
	{
		final PsiClass psiClass = getPsiClass(e);

		return super.isEnabled(e) && psiClass != null && psiClass.isPhysical();
	}

	@Override
	protected void perform(AnActionEvent e)
	{
		final PsiClass psiClass = getPsiClass(e);
		if(psiClass != null)
		{
			NavigationUtil.activateFileWithPsiElement(psiClass);
		}
	}

	@javax.annotation.Nullable
	private PsiClass getPsiClass(AnActionEvent e)
	{
		final ReferenceType selectedClass = getSelectedClass(e);
		final Project project = e.getProject();
		if(selectedClass == null || project == null)
		{
			return null;
		}

		final ReferenceType targetClass = getObjectType(selectedClass);
		if(targetClass != null)
		{
			return DebuggerUtils.findClass(targetClass.name(), project, GlobalSearchScope.allScope(project));
		}

		return null;
	}

	@javax.annotation.Nullable
	private static ReferenceType getObjectType(@Nonnull ReferenceType ref)
	{
		if(!(ref instanceof ArrayType))
		{
			return ref;
		}

		final String elementTypeName = ref.name().replace("[]", "");
		final VirtualMachine vm = ref.virtualMachine();
		final List<ReferenceType> referenceTypes = vm.classesByName(elementTypeName);
		if(referenceTypes.size() == 1)
		{
			return referenceTypes.get(0);
		}

		return null;
	}
}
