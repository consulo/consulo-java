/*
 * Copyright 2006-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.dependency;

import com.intellij.java.analysis.codeInspection.reference.RefClass;
import com.intellij.java.analysis.codeInspection.reference.RefJavaVisitor;
import com.intellij.java.impl.ig.BaseGlobalInspection;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.deadCodeNotWorking.impl.SingleIntegerFieldOptionsPanel;
import consulo.language.editor.inspection.CommonProblemDescriptor;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.ProblemDescriptionsProcessor;
import consulo.language.editor.inspection.reference.RefFile;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.Set;

public abstract class ClassWithTooManyDependenciesInspection extends BaseGlobalInspection
{

	@SuppressWarnings({"PublicField"})
	public int limit = 10;

	@Nonnull
	@Override
	public String getDisplayName()
	{
		return InspectionGadgetsLocalize.classWithTooManyDependenciesDisplayName().get();
	}

	@Override
	public void runInspection(
			AnalysisScope scope,
			final InspectionManager inspectionManager,
			GlobalInspectionContext globalInspectionContext,
			final ProblemDescriptionsProcessor problemDescriptionsProcessor,
			Object state)
	{
		final RefManager refManager = globalInspectionContext.getRefManager();
		refManager.iterate(new RefJavaVisitor()
		{

			@Override
			public void visitClass(@Nonnull RefClass refClass)
			{
				super.visitClass(refClass);
				if(!(refClass.getOwner() instanceof RefFile))
				{
					return;
				}
				final Set<RefClass> dependencies =
						DependencyUtils.calculateDependenciesForClass(refClass);
				final int numDependencies = dependencies.size();
				if(numDependencies <= limit)
				{
					return;
				}
				final String errorString = InspectionGadgetsLocalize.classWithTooManyDependenciesProblemDescriptor(
					refClass.getName(),
					numDependencies,
					limit
				).get();
				final CommonProblemDescriptor[] descriptors = {
					inspectionManager.createProblemDescriptor(errorString)
				};
				problemDescriptionsProcessor.addProblemElement(refClass, descriptors);
			}
		});
	}

	@Override
	public JComponent createOptionsPanel()
	{
		LocalizeValue message = InspectionGadgetsLocalize.classWithTooManyDependenciesMaxOption();
		return new SingleIntegerFieldOptionsPanel(message.get(), this, "limit");
	}
}
