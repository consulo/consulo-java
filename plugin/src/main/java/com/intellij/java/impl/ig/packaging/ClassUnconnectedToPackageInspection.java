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
package com.intellij.java.impl.ig.packaging;

import com.intellij.java.analysis.codeInspection.reference.RefClass;
import com.intellij.java.analysis.codeInspection.reference.RefPackage;
import com.intellij.java.impl.ig.BaseGlobalInspection;
import com.intellij.java.impl.ig.dependency.DependencyUtils;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiIdentifier;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.language.editor.inspection.CommonProblemDescriptor;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Set;

public abstract class ClassUnconnectedToPackageInspection extends BaseGlobalInspection
{
	@Nonnull
	@Override
	public String getDisplayName()
	{
		return InspectionGadgetsLocalize.classUnconnectedToPackageDisplayName().get();
	}

	@Override
	@Nullable
	public CommonProblemDescriptor[] checkElement(
			RefEntity refEntity,
			AnalysisScope analysisScope,
			InspectionManager manager,
			GlobalInspectionContext globalInspectionContext,
			Object state)
	{
		if(!(refEntity instanceof RefClass))
		{
			return null;
		}
		final RefClass refClass = (RefClass) refEntity;
		final RefEntity owner = refClass.getOwner();
		if(!(owner instanceof RefPackage))
		{
			return null;
		}

		final Set<RefClass> dependencies =
				DependencyUtils.calculateDependenciesForClass(refClass);
		for(RefClass dependency : dependencies)
		{
			if(inSamePackage(refClass, dependency))
			{
				return null;
			}
		}
		final Set<RefClass> dependents =
				DependencyUtils.calculateDependentsForClass(refClass);
		for(RefClass dependent : dependents)
		{
			if(inSamePackage(refClass, dependent))
			{
				return null;
			}
		}
		final PsiClass aClass = refClass.getElement();
		final PsiIdentifier identifier = aClass.getNameIdentifier();
		if(identifier == null)
		{
			return null;
		}
		return new CommonProblemDescriptor[]{
			manager.createProblemDescriptor(
				identifier,
				InspectionGadgetsLocalize.classUnconnectedToPackageProblemDescriptor().get(),
				true,
				ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
				false
			)
		};
	}

	private static boolean inSamePackage(RefClass class1, RefClass class2)
	{
		return class1.getOwner() == class2.getOwner();
	}
}
