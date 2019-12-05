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
package com.intellij.codeInsight.daemon.impl;

import javax.annotation.Nonnull;

import com.intellij.ProjectTopics;
import com.intellij.core.JavaCoreBundle;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import consulo.util.dataholder.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import consulo.annotation.access.RequiredReadAction;
import consulo.editor.notifications.EditorNotificationProvider;
import consulo.java.module.extension.JavaModuleExtension;
import consulo.module.extension.ModuleExtension;

/**
 * @author Danila Ponomarenko
 */
public class SetupJDKNotificationProvider implements EditorNotificationProvider<EditorNotificationPanel>
{
	private static final Key<EditorNotificationPanel> KEY = Key.create("setup.jdk.notifier");

	private final Project myProject;

	public SetupJDKNotificationProvider(Project project, final EditorNotifications notifications)
	{
		myProject = project;
		myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter()
		{
			@Override
			public void rootsChanged(ModuleRootEvent event)
			{
				notifications.updateAllNotifications();
			}
		});
		myProject.getMessageBus().connect().subscribe(ModuleExtension.CHANGE_TOPIC, (oldExtension, newExtension) -> notifications.updateAllNotifications());
	}

	@Nonnull
	@Override
	public Key<EditorNotificationPanel> getKey()
	{
		return KEY;
	}

	@RequiredReadAction
	@Override
	public EditorNotificationPanel createNotificationPanel(@Nonnull VirtualFile file, @Nonnull FileEditor fileEditor)
	{
		if(file.getFileType() == JavaClassFileType.INSTANCE)
		{
			return null;
		}

		final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
		if(psiFile == null)
		{
			return null;
		}

		if(psiFile.getLanguage() != JavaLanguage.INSTANCE)
		{
			return null;
		}

		final Module moduleForPsiElement = ModuleUtilCore.findModuleForPsiElement(psiFile);
		if(moduleForPsiElement == null)
		{
			return null;
		}
		final JavaModuleExtension extension = ModuleUtilCore.getExtension(moduleForPsiElement, JavaModuleExtension.class);
		if(extension == null)
		{
			return null;
		}

		if(extension.getInheritableSdk().isNull())
		{
			return createPanel(myProject, psiFile);
		}
		return null;
	}

	@Nonnull
	private static EditorNotificationPanel createPanel(final @Nonnull Project project, final @Nonnull PsiFile file)
	{
		EditorNotificationPanel panel = new EditorNotificationPanel();
		panel.setText(JavaCoreBundle.message("module.jdk.not.defined"));
		panel.createActionLabel(JavaCoreBundle.message("module.jdk.setup"), () ->
		{
			final Module moduleForPsiElement = ModuleUtilCore.findModuleForPsiElement(file);
			if(moduleForPsiElement == null)
			{
				return;
			}

			ProjectSettingsService.getInstance(project).openModuleSettings(moduleForPsiElement);
		});
		return panel;
	}
}
