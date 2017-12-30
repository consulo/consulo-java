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

/*
 * User: anna
 * Date: 03-Nov-2009
 */
package com.intellij.codeInspection.inconsistentLanguageLevel;

import gnu.trove.THashSet;

import java.util.Set;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.codeInspection.unnecessaryModuleDependency.UnnecessaryModuleDependencyInspection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;

public class InconsistentLanguageLevelInspection extends GlobalInspectionTool
{
	private static final Logger LOGGER = Logger.getInstance(InconsistentLanguageLevelInspection.class);

	@Override
	public boolean isGraphNeeded()
	{
		return false;
	}

	@Override
	public void runInspection(@NotNull AnalysisScope scope, @NotNull InspectionManager manager, @NotNull GlobalInspectionContext globalContext, @NotNull ProblemDescriptionsProcessor problemProcessor)
	{
		final Set<Module> modules = new THashSet<Module>();
		scope.accept(new PsiElementVisitor()
		{
			@Override
			public void visitElement(PsiElement element)
			{
				final Module module = ModuleUtilCore.findModuleForPsiElement(element);
				if(module != null)
				{
					modules.add(module);
				}
			}
		});


		for(Module module : modules)
		{
			LanguageLevel languageLevel = EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);

			final RefModule refModule = globalContext.getRefManager().getRefModule(module);
			for(OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries())
			{
				if(!(entry instanceof ModuleOrderEntry))
				{
					continue;
				}
				final Module dependantModule = ((ModuleOrderEntry) entry).getModule();
				if(dependantModule == null)
				{
					continue;
				}
				LanguageLevel dependantLanguageLevel = EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(dependantModule);
				if(languageLevel.compareTo(dependantLanguageLevel) < 0)
				{
					final CommonProblemDescriptor problemDescriptor = manager.createProblemDescriptor("Inconsistent language level settings: module " + module.getName() + " with language level " +
							languageLevel + " depends on module " + dependantModule.getName() + " with language level " + dependantLanguageLevel, new UnnecessaryModuleDependencyInspection
							.RemoveModuleDependencyFix(module, dependantModule), new OpenModuleSettingsFix(module));
					problemProcessor.addProblemElement(refModule, problemDescriptor);
				}
			}
		}
	}

	@Override
	public boolean isEnabledByDefault()
	{
		return false;
	}

	@Override
	@Nls
	@NotNull
	public String getGroupDisplayName()
	{
		return GroupNames.MODULARIZATION_GROUP_NAME;
	}

	@Override
	@NotNull
	public String getDisplayName()
	{
		return "Inconsistent language level settings";
	}

	@Override
	@NonNls
	@NotNull
	public String getShortName()
	{
		return "InconsistentLanguageLevel";
	}

	private static class OpenModuleSettingsFix implements QuickFix
	{
		private final Module myModule;

		private OpenModuleSettingsFix(Module module)
		{
			myModule = module;
		}

		@Override
		@NotNull
		public String getName()
		{
			return "Open module " + myModule.getName() + " settings";
		}

		@Override
		@NotNull
		public String getFamilyName()
		{
			return getName();
		}

		@Override
		public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor)
		{
			if(!myModule.isDisposed())
			{
				ProjectSettingsService.getInstance(project).showModuleConfigurationDialog(myModule.getName(), ProjectBundle.message("modules.classpath.title"));
			}
		}
	}
}