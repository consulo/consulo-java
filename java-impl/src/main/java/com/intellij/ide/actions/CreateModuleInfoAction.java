/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import static com.intellij.ide.fileTemplates.JavaTemplateUtil.INTERNAL_MODULE_INFO_TEMPLATE_NAME;
import static com.intellij.psi.PsiJavaModule.MODULE_INFO_CLASS;
import static com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.actions.AttributesDefaults;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.util.PsiUtil;
import consulo.awt.TargetAWT;
import consulo.java.JavaBundle;
import consulo.roots.ContentFolderScopes;

public class CreateModuleInfoAction extends CreateFromTemplateActionBase
{
	public CreateModuleInfoAction()
	{
		super(JavaBundle.message("action.create.new.module-info.title"), JavaBundle.message("action.create.new.module-info.description"), TargetAWT.to(JavaFileType.INSTANCE.getIcon()));
	}

	@Override
	public void update(@Nonnull AnActionEvent e)
	{
		if(!e.getPresentation().isVisible())
		{
			return;
		}
		DataContext ctx = e.getDataContext();
		boolean available = Optional.ofNullable(ctx.getData(LangDataKeys.IDE_VIEW)).map(view -> getTargetDirectory(ctx, view)).filter(PsiUtil::isLanguageLevel9OrHigher).map
				(ModuleUtilCore::findModuleForPsiElement).map(module -> FilenameIndex.getVirtualFilesByName(module.getProject(), MODULE_INFO_FILE, module.getModuleScope()).isEmpty()).orElse(false);
		e.getPresentation().setEnabledAndVisible(available);
	}

	@javax.annotation.Nullable
	@Override
	protected PsiDirectory getTargetDirectory(DataContext ctx, IdeView view)
	{
		PsiDirectory[] directories = view.getDirectories();
		if(directories.length == 1)
		{
			PsiDirectory psiDir = directories[0];
			VirtualFile vDir = psiDir.getVirtualFile();
			ProjectFileIndex index = ProjectRootManager.getInstance(psiDir.getProject()).getFileIndex();
			if(vDir.equals(index.getSourceRootForFile(vDir)) && index.isUnderContentFolderType(vDir, ContentFolderScopes.onlyProduction()))
			{
				return psiDir;
			}
		}

		return null;
	}

	@Override
	protected FileTemplate getTemplate(@Nonnull Project project, @Nonnull PsiDirectory dir)
	{
		return FileTemplateManager.getInstance(project).getInternalTemplate(INTERNAL_MODULE_INFO_TEMPLATE_NAME);
	}

	@Override
	protected AttributesDefaults getAttributesDefaults(@Nonnull DataContext ctx)
	{
		return new AttributesDefaults(MODULE_INFO_CLASS).withFixedName(true);
	}

	@Override
	protected Map<String, String> getLiveTemplateDefaults(@Nonnull DataContext ctx, @Nonnull PsiFile file)
	{
		Module module = ctx.getData(LangDataKeys.MODULE);
		return Collections.singletonMap("MODULE_NAME", module != null ? LightJavaModule.moduleName(module.getName()) : "module_name");
	}
}