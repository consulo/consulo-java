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
package com.intellij.compiler.actions;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.impl.javaCompiler.AnnotationProcessingCompiler;
import com.intellij.compiler.impl.javaCompiler.JavaCompilerConfiguration;
import com.intellij.compiler.impl.javaCompiler.annotationProcessing.AnnotationProcessingConfiguration;
import com.intellij.compiler.impl.resourceCompiler.ResourceCompiler;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaPackage;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ContainerUtil;
import consulo.ui.annotation.RequiredUIAccess;

public class ProcessAnnotationsAction extends CompileActionBase
{
	@RequiredUIAccess
	@Override
	protected void doAction(DataContext dataContext, Project project)
	{
		final Module module = dataContext.getData(LangDataKeys.MODULE_CONTEXT);
		final Condition<com.intellij.openapi.compiler.Compiler> filter = new Condition<com.intellij.openapi.compiler.Compiler>()
		{
			@Override
			public boolean value(com.intellij.openapi.compiler.Compiler compiler)
			{
				// EclipseLink CanonicalModelProcessor reads input from output hence adding ResourcesCompiler
				return compiler instanceof AnnotationProcessingCompiler || compiler instanceof ResourceCompiler;
			}
		};
		if(module != null)
		{
			CompilerManager.getInstance(project).make(new ModuleCompileScope(module, false), filter, null);
		}
		else
		{
			final FileSetCompileScope scope = getCompilableFiles(project, dataContext.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY));
			if(scope != null)
			{
				CompilerManager.getInstance(project).make(scope, filter, null);
			}
		}
	}

	@RequiredUIAccess
	@Override
	public void update(@Nonnull AnActionEvent event)
	{
		super.update(event);
		Presentation presentation = event.getPresentation();
		if(!presentation.isEnabled())
		{
			return;
		}
		presentation.setVisible(false);

		Project project = event.getData(CommonDataKeys.PROJECT);
		if(project == null)
		{
			presentation.setEnabled(false);
			return;
		}

		final JavaCompilerConfiguration compilerConfiguration = JavaCompilerConfiguration.getInstance(project);

		final Module module = event.getData(LangDataKeys.MODULE);
		final Module moduleContext = event.getData(LangDataKeys.MODULE_CONTEXT);

		if(module == null)
		{
			presentation.setEnabled(false);
			return;
		}
		final AnnotationProcessingConfiguration profile = compilerConfiguration.getAnnotationProcessingConfiguration(module);
		if(!profile.isEnabled() || (!profile.isObtainProcessorsFromClasspath() && profile.getProcessors().isEmpty()))
		{
			presentation.setEnabled(false);
			return;
		}

		presentation.setVisible(true);
		presentation.setText(createPresentationText(""), true);
		final FileSetCompileScope scope = getCompilableFiles(project, event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY));
		if(moduleContext == null && scope == null)
		{
			presentation.setEnabled(false);
			return;
		}

		String elementDescription = null;
		if(moduleContext != null)
		{
			elementDescription = CompilerBundle.message("action.compile.description.module", moduleContext.getName());
		}
		else
		{
			PsiJavaPackage aPackage = null;
			final Collection<VirtualFile> files = scope.getRootFiles();
			if(files.size() == 1)
			{
				final PsiDirectory directory = PsiManager.getInstance(project).findDirectory(files.iterator().next());
				if(directory != null)
				{
					aPackage = JavaDirectoryService.getInstance().getPackage(directory);
				}
			}
			else
			{
				PsiElement element = event.getData(LangDataKeys.PSI_ELEMENT);
				if(element instanceof PsiJavaPackage)
				{
					aPackage = (PsiJavaPackage) element;
				}
			}

			if(aPackage != null)
			{
				String name = aPackage.getQualifiedName();
				if(name.length() == 0)
				{
					//noinspection HardCodedStringLiteral
					name = "<default>";
				}
				elementDescription = "'" + name + "'";
			}
			else if(files.size() == 1)
			{
				final VirtualFile file = files.iterator().next();
				FileType fileType = file.getFileType();
				if(CompilerManager.getInstance(project).isCompilableFileType(fileType))
				{
					elementDescription = "'" + file.getName() + "'";
				}
				else
				{
					if(!ActionPlaces.MAIN_MENU.equals(event.getPlace()))
					{
						// the action should be invisible in popups for non-java files
						presentation.setEnabled(false);
						presentation.setVisible(false);
						return;
					}
				}
			}
			else
			{
				elementDescription = CompilerBundle.message("action.compile.description.selected.files");
			}
		}

		if(elementDescription == null)
		{
			presentation.setEnabled(false);
			return;
		}

		presentation.setText(createPresentationText(elementDescription), true);
		presentation.setEnabled(true);
	}

	private static String createPresentationText(final String elementDescription)
	{
		int length = elementDescription.length();
		String target = length > 23 ? (StringUtil.startsWithChar(elementDescription, '\'') ? "'..." : "...") + elementDescription.substring(length -
				20, length) : elementDescription;
		return MessageFormat.format(ActionsBundle.actionText(StringUtil.isEmpty(target) ? "RunAPT" : "RunAPT.1"), target);
	}

	@javax.annotation.Nullable
	private static FileSetCompileScope getCompilableFiles(Project project, VirtualFile[] files)
	{
		if(files == null || files.length == 0)
		{
			return null;
		}
		final PsiManager psiManager = PsiManager.getInstance(project);
		final FileTypeManager typeManager = FileTypeManager.getInstance();
		final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
		final CompilerManager compilerManager = CompilerManager.getInstance(project);
		final List<VirtualFile> filesToCompile = new ArrayList<VirtualFile>();
		final List<Module> affectedModules = new ArrayList<Module>();
		for(final VirtualFile file : files)
		{
			if(!fileIndex.isInSourceContent(file))
			{
				continue;
			}
			if(!file.isInLocalFileSystem())
			{
				continue;
			}
			if(file.isDirectory())
			{
				final PsiDirectory directory = psiManager.findDirectory(file);
				if(directory == null || JavaDirectoryService.getInstance().getPackage(directory) == null)
				{
					continue;
				}
			}
			else
			{
				FileType fileType = file.getFileType();
				if(!(compilerManager.isCompilableFileType(fileType)))
				{
					continue;
				}
			}
			filesToCompile.add(file);
			ContainerUtil.addIfNotNull(fileIndex.getModuleForFile(file), affectedModules);
		}
		if(filesToCompile.isEmpty())
		{
			return null;
		}
		return new FileSetCompileScope(filesToCompile, affectedModules.toArray(new Module[affectedModules.size()]));
	}
}