/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.externalSystem;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.pom.java.LanguageLevel;
import consulo.java.module.extension.JavaMutableModuleExtension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 4/15/13 12:09 PM
 */
public class JavaProjectDataService implements ProjectDataService<JavaProjectData, Project>
{
	@Nonnull
	@Override
	public Key<JavaProjectData> getTargetDataKey()
	{
		return JavaProjectData.KEY;
	}

	@Override
	public void importData(@Nonnull Collection<DataNode<JavaProjectData>> toImport, @Nonnull final Project project, boolean synchronous)
	{
		if(toImport.size() != 1)
		{
			throw new IllegalArgumentException(String.format("Expected to get a single project but got %d: %s", toImport.size(), toImport));
		}
		JavaProjectData projectData = toImport.iterator().next().getData();

		final Sdk jdk = findJdk(projectData.getJdkVersion());
		final LanguageLevel languageLevel = projectData.getLanguageLevel();

		ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(project)
		{
			@Override
			public void execute()
			{
				ModuleManager moduleManager = ModuleManager.getInstance(project);

				for(Module module : moduleManager.getModules())
				{
					if(module.isDisposed())
					{
						continue;
					}
					ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

					ModifiableRootModel modifiableModel = moduleRootManager.getModifiableModel();

					JavaMutableModuleExtension<?> e = modifiableModel.getExtensionWithoutCheck("java");
					if(!e.isEnabled())
					{
						e.setEnabled(true);
						modifiableModel.addModuleExtensionSdkEntry(e);
					}
					if(jdk != null)
					{
						e.getInheritableSdk().set(null, jdk);
					}
					e.getInheritableLanguageLevel().set(null, languageLevel);
					modifiableModel.commit();
				}
			}
		});
	}

	@Nullable
	private static Sdk findJdk(@Nonnull JavaSdkVersion version)
	{
		JavaSdk javaSdk = JavaSdk.getInstance();
		List<Sdk> javaSdks = SdkTable.getInstance().getSdksOfType(javaSdk);
		Sdk candidate = null;
		for(Sdk sdk : javaSdks)
		{
			JavaSdkVersion v = javaSdk.getVersion(sdk);
			if(v == version)
			{
				return sdk;
			}
			else if(candidate == null && v != null && version.getMaxLanguageLevel().isAtLeast(version.getMaxLanguageLevel()))
			{
				candidate = sdk;
			}
		}
		return candidate;
	}

	@Override
	public void removeData(@Nonnull Collection<? extends Project> toRemove, @Nonnull Project project, boolean synchronous)
	{
	}
}
