/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.deadCode;

import javax.annotation.Nonnull;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.ex.JobDescriptor;

/**
 * @author max
 */
public class DummyEntryPointsTool extends UnusedDeclarationInspection
{
	public DummyEntryPointsTool()
	{
	}

	@Override
	public void runInspection(@Nonnull AnalysisScope scope,
			@Nonnull InspectionManager manager,
			@Nonnull GlobalInspectionContext globalContext,
			@Nonnull ProblemDescriptionsProcessor problemDescriptionsProcessor)
	{
	}

	@javax.annotation.Nullable
	@Override
	public JobDescriptor[] getAdditionalJobs()
	{
		return JobDescriptor.EMPTY_ARRAY;
	}

	@Override
	@Nonnull
	public String getDisplayName()
	{
		return InspectionsBundle.message("inspection.dead.code.entry.points.display.name");
	}

	@Override
	@Nonnull
	public String getGroupDisplayName()
	{
		return "";
	}

	@Override
	@Nonnull
	public String getShortName()
	{
		//noinspection InspectionDescriptionNotFoundInspection
		return "";
	}
}
