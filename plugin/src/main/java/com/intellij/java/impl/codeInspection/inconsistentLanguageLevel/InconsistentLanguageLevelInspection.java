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
package com.intellij.java.impl.codeInspection.inconsistentLanguageLevel;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefModule;
import com.intellij.java.impl.codeInspection.unnecessaryModuleDependency.UnnecessaryModuleDependencyInspection;
import com.intellij.java.language.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.java.language.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import consulo.logging.Logger;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public class InconsistentLanguageLevelInspection extends GlobalInspectionTool
{
	private static final Logger LOGGER = Logger.getInstance(InconsistentLanguageLevelInspection.class);

	@Override
	public boolean isGraphNeeded()
	{
		return false;
	}

	@Override
	public void runInspection(@Nonnull AnalysisScope scope, @Nonnull InspectionManager manager, @Nonnull GlobalInspectionContext globalContext, @Nonnull ProblemDescriptionsProcessor problemProcessor)
	{
		final Set<Module> modules = new HashSet<Module>();
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
	@Nonnull
	public String getGroupDisplayName()
	{
		return GroupNames.MODULARIZATION_GROUP_NAME;
	}

	@Override
	@Nonnull
	public String getDisplayName()
	{
		return "Inconsistent language level settings";
	}

	@Override
	@NonNls
	@Nonnull
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
		@Nonnull
		public String getName()
		{
			return "Open module " + myModule.getName() + " settings";
		}

		@Override
		@Nonnull
		public String getFamilyName()
		{
			return getName();
		}

		@Override
		public void applyFix(@Nonnull Project project, @Nonnull CommonProblemDescriptor descriptor)
		{
			if(!myModule.isDisposed())
			{
				ProjectSettingsService.getInstance(project).showModuleConfigurationDialog(myModule.getName(), ProjectBundle.message("modules.classpath.title"));
			}
		}
	}
}
