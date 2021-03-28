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

package com.intellij.psi.impl.file;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import com.intellij.core.CoreJavaDirectoryService;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog;
import com.intellij.ide.highlighter.JavaFileType;
import consulo.logging.Logger;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaPackage;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import consulo.java.module.extension.JavaModuleExtension;

/**
 * @author max
 */
@Singleton
public class JavaDirectoryServiceImpl extends CoreJavaDirectoryService
{
	private static final Logger LOG = Logger.getInstance(JavaDirectoryServiceImpl.class);

	@Override
	public PsiJavaPackage getPackage(@Nonnull PsiDirectory dir)
	{
		ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(dir.getProject()).getFileIndex();
		String packageName = projectFileIndex.getPackageNameByDirectory(dir.getVirtualFile());
		if(packageName == null)
		{
			return null;
		}
		return JavaPsiFacade.getInstance(dir.getProject()).findPackage(packageName);
	}

	@Override
	@Nonnull
	public PsiClass createClass(@Nonnull PsiDirectory dir, @Nonnull String name) throws IncorrectOperationException
	{
		return createClassFromTemplate(dir, name, JavaTemplateUtil.INTERNAL_CLASS_TEMPLATE_NAME);
	}

	@Override
	@Nonnull
	public PsiClass createClass(@Nonnull PsiDirectory dir, @Nonnull String name, @Nonnull String templateName) throws IncorrectOperationException
	{
		return createClassFromTemplate(dir, name, templateName);
	}

	@Override
	public PsiClass createClass(@Nonnull PsiDirectory dir, @Nonnull String name, @Nonnull String templateName, boolean askForUndefinedVariables) throws IncorrectOperationException
	{
		return createClass(dir, name, templateName, askForUndefinedVariables, Collections.<String, String>emptyMap());
	}

	@Override
	public PsiClass createClass(@Nonnull PsiDirectory dir,
			@Nonnull String name,
			@Nonnull String templateName,
			boolean askForUndefinedVariables,
			@Nonnull final Map<String, String> additionalProperties) throws IncorrectOperationException
	{
		return createClassFromTemplate(dir, name, templateName, askForUndefinedVariables, additionalProperties);
	}

	@Override
	@Nonnull
	public PsiClass createInterface(@Nonnull PsiDirectory dir, @Nonnull String name) throws IncorrectOperationException
	{
		String templateName = JavaTemplateUtil.INTERNAL_INTERFACE_TEMPLATE_NAME;
		PsiClass someClass = createClassFromTemplate(dir, name, templateName);
		if(!someClass.isInterface())
		{
			throw new IncorrectOperationException(getIncorrectTemplateMessage(dir.getProject(), templateName));
		}
		return someClass;
	}

	@Override
	@Nonnull
	public PsiClass createEnum(@Nonnull PsiDirectory dir, @Nonnull String name) throws IncorrectOperationException
	{
		String templateName = JavaTemplateUtil.INTERNAL_ENUM_TEMPLATE_NAME;
		PsiClass someClass = createClassFromTemplate(dir, name, templateName);
		if(!someClass.isEnum())
		{
			throw new IncorrectOperationException(getIncorrectTemplateMessage(dir.getProject(), templateName));
		}
		return someClass;
	}

	@Override
	@Nonnull
	public PsiClass createAnnotationType(@Nonnull PsiDirectory dir, @Nonnull String name) throws IncorrectOperationException
	{
		String templateName = JavaTemplateUtil.INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME;
		PsiClass someClass = createClassFromTemplate(dir, name, templateName);
		if(!someClass.isAnnotationType())
		{
			throw new IncorrectOperationException(getIncorrectTemplateMessage(dir.getProject(), templateName));
		}
		return someClass;
	}

	private static PsiClass createClassFromTemplate(@Nonnull PsiDirectory dir, String name, String templateName) throws IncorrectOperationException
	{
		return createClassFromTemplate(dir, name, templateName, false, Collections.<String, String>emptyMap());
	}

	private static PsiClass createClassFromTemplate(@Nonnull PsiDirectory dir,
			String name,
			String templateName,
			boolean askToDefineVariables,
			@Nonnull Map<String, String> additionalProperties) throws IncorrectOperationException
	{
		//checkCreateClassOrInterface(dir, name);

		Project project = dir.getProject();
		FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate(templateName);

		Properties defaultProperties = FileTemplateManager.getInstance(project).getDefaultProperties();
		Properties properties = new Properties(defaultProperties);
		properties.setProperty(FileTemplate.ATTRIBUTE_NAME, name);
		for(Map.Entry<String, String> entry : additionalProperties.entrySet())
		{
			properties.setProperty(entry.getKey(), entry.getValue());
		}

		String ext = JavaFileType.INSTANCE.getDefaultExtension();
		String fileName = name + "." + ext;

		PsiElement element;
		try
		{
			element = askToDefineVariables ? new CreateFromTemplateDialog(project, dir, template, null, properties).create() : FileTemplateUtil.createFromTemplate(template, fileName, properties,
					dir);
		}
		catch(IncorrectOperationException e)
		{
			throw e;
		}
		catch(Exception e)
		{
			LOG.error(e);
			return null;
		}
		if(element == null)
		{
			return null;
		}
		final PsiJavaFile file = (PsiJavaFile) element.getContainingFile();
		PsiClass[] classes = file.getClasses();
		if(classes.length < 1)
		{
			throw new IncorrectOperationException(getIncorrectTemplateMessage(dir.getProject(), templateName));
		}
		return classes[0];
	}

	private static String getIncorrectTemplateMessage(Project project, String templateName)
	{
		return PsiBundle.message("psi.error.incorroect.class.template.message", FileTemplateManager.getInstance(project).internalTemplateToSubject(templateName), templateName);
	}

	@Override
	public void checkCreateClass(@Nonnull PsiDirectory dir, @Nonnull String name) throws IncorrectOperationException
	{
		checkCreateClassOrInterface(dir, name);
	}

	public static void checkCreateClassOrInterface(@Nonnull PsiDirectory directory, String name) throws IncorrectOperationException
	{
		PsiUtil.checkIsIdentifier(directory.getManager(), name);

		String fileName = name + "." + JavaFileType.INSTANCE.getDefaultExtension();
		directory.checkCreateFile(fileName);

		PsiNameHelper helper = PsiNameHelper.getInstance(directory.getProject());
		PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
		String qualifiedName = aPackage == null ? null : aPackage.getQualifiedName();
		if(!StringUtil.isEmpty(qualifiedName) && !helper.isQualifiedName(qualifiedName))
		{
			throw new IncorrectOperationException("Cannot create class in invalid package: '" + qualifiedName + "'");
		}
	}

	@Override
	public boolean isSourceRoot(@Nonnull PsiDirectory dir)
	{
		final VirtualFile file = dir.getVirtualFile();
		final VirtualFile sourceRoot = ProjectRootManager.getInstance(dir.getProject()).getFileIndex().getSourceRootForFile(file);
		return file.equals(sourceRoot);
	}

	@Override
	public LanguageLevel getLanguageLevel(@Nonnull PsiDirectory dir)
	{
		JavaModuleExtension extension = ModuleUtilCore.getExtension(dir, JavaModuleExtension.class);
		return extension == null ? LanguageLevel.HIGHEST : extension.getLanguageLevel();
	}
}
