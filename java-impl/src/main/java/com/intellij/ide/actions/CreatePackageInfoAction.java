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
package com.intellij.ide.actions;

import static com.intellij.ide.fileTemplates.JavaTemplateUtil.INTERNAL_PACKAGE_INFO_TEMPLATE_NAME;

import org.jetbrains.annotations.Nullable;
import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.actions.AttributesDefaults;
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateActionBase;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaPackage;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.util.PsiUtil;
import consulo.java.JavaBundle;
import consulo.roots.ContentFolderScopes;

/**
 * @author Bas Leijdekkers
 */
public class CreatePackageInfoAction extends CreateFromTemplateActionBase implements DumbAware
{
	public CreatePackageInfoAction()
	{
		super(JavaBundle.message("action.create.new.package-info.title"), JavaBundle.message("action.create.new.package-info.description"), JavaFileType.INSTANCE.getIcon());
	}

	@Nullable
	@Override
	protected PsiDirectory getTargetDirectory(DataContext dataContext, IdeView view)
	{
		final PsiDirectory[] directories = view.getDirectories();
		for(PsiDirectory directory : directories)
		{
			final PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
			if(aPackage == null)
			{
				continue;
			}
			if(directory.findFile(PsiJavaPackage.PACKAGE_INFO_FILE) != null)
			{
				Messages.showErrorDialog(dataContext.getData(CommonDataKeys.PROJECT), JavaBundle.message("error.package.already.contains.package-info", aPackage.getQualifiedName()), IdeBundle
						.message("title.cannot.create.file"));
				return null;
			}
			else if(directory.findFile("package.html") != null)
			{
				if(Messages.showOkCancelDialog(dataContext.getData(CommonDataKeys.PROJECT), JavaBundle.message("error.package.already.contains.package.html", aPackage.getQualifiedName()), JavaBundle
						.message("error.package.html.found.title"), IdeBundle.message("button.create"), CommonBundle.message("button.cancel"), Messages.getQuestionIcon()) != Messages.OK)
				{
					return null;
				}
			}

		}
		return super.getTargetDirectory(dataContext, view);
	}

	@Override
	public void update(AnActionEvent e)
	{
		if(!e.getPresentation().isVisible())
		{
			return;
		}

		e.getPresentation().setEnabledAndVisible(isAvailable(e.getDataContext()));
	}

	private static boolean isAvailable(DataContext dataContext)
	{
		final Project project = dataContext.getData(CommonDataKeys.PROJECT);
		final IdeView view = dataContext.getData(LangDataKeys.IDE_VIEW);
		if(project == null || view == null)
		{
			return false;
		}
		final PsiDirectory[] directories = view.getDirectories();
		if(directories.length == 0)
		{
			return false;
		}
		final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
		final JavaDirectoryService directoryService = JavaDirectoryService.getInstance();
		final PsiNameHelper nameHelper = PsiNameHelper.getInstance(project);
		for(PsiDirectory directory : directories)
		{
			if(projectFileIndex.isUnderContentFolderType(directory.getVirtualFile(), ContentFolderScopes.productionAndTest()) && PsiUtil.isLanguageLevel5OrHigher(directory))
			{
				final PsiJavaPackage aPackage = directoryService.getPackage(directory);
				if(aPackage != null)
				{
					final String qualifiedName = aPackage.getQualifiedName();
					if(StringUtil.isEmpty(qualifiedName) || nameHelper.isQualifiedName(qualifiedName))
					{
						return true;
					}
				}
			}

		}
		return false;
	}

	@Nullable
	@Override
	public AttributesDefaults getAttributesDefaults(DataContext dataContext)
	{
		return new AttributesDefaults(INTERNAL_PACKAGE_INFO_TEMPLATE_NAME).withFixedName(true);
	}

	@Override
	protected FileTemplate getTemplate(Project project, PsiDirectory dir)
	{
		return FileTemplateManager.getInstance(project).getInternalTemplate(INTERNAL_PACKAGE_INFO_TEMPLATE_NAME);
	}
}