/*
 * Copyright 2011-2012 Bas Leijdekkers
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
import com.intellij.java.analysis.codeInspection.reference.RefJavaUtil;
import com.intellij.java.analysis.codeInspection.reference.RefPackage;
import com.intellij.java.impl.ig.BaseGlobalInspection;
import com.intellij.java.impl.ig.dependency.DependencyUtils;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiIdentifier;
import com.siyeh.InspectionGadgetsBundle;
import consulo.language.editor.inspection.CommonProblemDescriptor;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

public abstract class ClassOnlyUsedInOnePackageInspection extends BaseGlobalInspection
{
	@Nls
	@Nonnull
	@Override
	public String getDisplayName()
	{
		return InspectionGadgetsBundle.message("class.only.used.in.one.package.display.name");
	}

	@Nullable
	@Override
	public CommonProblemDescriptor[] checkElement(RefEntity refEntity,
												  AnalysisScope scope,
												  InspectionManager manager,
												  GlobalInspectionContext globalContext,
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
		final Set<RefClass> dependencies = DependencyUtils.calculateDependenciesForClass(refClass);
		RefPackage otherPackage = null;
		for(RefClass dependency : dependencies)
		{
			final RefPackage refPackage = RefJavaUtil.getPackage(dependency);
			if(owner == refPackage)
			{
				return null;
			}
			if(otherPackage != refPackage)
			{
				if(otherPackage == null)
				{
					otherPackage = refPackage;
				}
				else
				{
					return null;
				}
			}
		}
		final Set<RefClass> dependents = DependencyUtils.calculateDependentsForClass(refClass);
		for(RefClass dependent : dependents)
		{
			final RefPackage refPackage = RefJavaUtil.getPackage(dependent);
			if(owner == refPackage)
			{
				return null;
			}
			if(otherPackage != refPackage)
			{
				if(otherPackage == null)
				{
					otherPackage = refPackage;
				}
				else
				{
					return null;
				}
			}
		}
		if(otherPackage == null)
		{
			return null;
		}
		final PsiClass aClass = refClass.getElement();
		final PsiIdentifier identifier = aClass.getNameIdentifier();
		if(identifier == null)
		{
			return null;
		}
		final String packageName = otherPackage.getName();
		return new CommonProblemDescriptor[]{
				manager.createProblemDescriptor(identifier, InspectionGadgetsBundle
								.message("class.only.used.in.one.package.problem.descriptor", packageName), true, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
						false)
		};
	}
}
