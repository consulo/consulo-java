/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.java.module.extension.JavaModuleExtension;
import com.intellij.core.JavaCoreBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaCreateFromTemplateHandler;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.module.EffectiveLanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaPackage;
import com.intellij.psi.PsiNameHelper;
import com.intellij.util.IncorrectOperationException;
import consulo.module.extension.ModuleExtension;

/**
 * The standard "New Class" action.
 *
 * @since 5.1
 */
public class CreateClassAction extends JavaCreateTemplateInPackageAction<PsiClass>
{
	public CreateClassAction()
	{
		super(null, null, AllIcons.Nodes.Class, true);
	}

	@Override
	protected void buildDialog(final Project project, PsiDirectory directory, CreateFileFromTemplateDialog.Builder builder)
	{
		builder.setTitle(JavaCoreBundle.message("action.create.new.class"))
				.addKind("Class", AllIcons.Nodes.Class, JavaTemplateUtil.INTERNAL_CLASS_TEMPLATE_NAME)
				.addKind("Interface", AllIcons.Nodes.Interface, JavaTemplateUtil.INTERNAL_INTERFACE_TEMPLATE_NAME);

		Module module = ModuleUtilCore.findModuleForPsiElement(directory);
		assert module != null;
		LanguageLevel languageLevel = EffectiveLanguageLevelUtil.getEffectiveLanguageLevel(module);

		if(languageLevel.isAtLeast(LanguageLevel.JDK_1_5))
		{
			builder.addKind("Enum", AllIcons.Nodes.Enum, JavaTemplateUtil.INTERNAL_ENUM_TEMPLATE_NAME);
			builder.addKind("Annotation", AllIcons.Nodes.Annotationtype, JavaTemplateUtil.INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME);
		}

		for(FileTemplate template : FileTemplateManager.getInstance().getAllTemplates())
		{
			final JavaCreateFromTemplateHandler handler = new JavaCreateFromTemplateHandler();
			if(handler.handlesTemplate(template) && JavaCreateFromTemplateHandler.canCreate(directory))
			{
				builder.addKind(template.getName(), JavaFileType.INSTANCE.getIcon(), template.getName());
			}
		}

		builder.setValidator(new InputValidatorEx()
		{
			@Override
			public String getErrorText(String inputString)
			{
				if(inputString.length() > 0 && !PsiNameHelper.getInstance(project).isQualifiedName(inputString))
				{
					return "This is not a valid Java qualified name";
				}
				return null;
			}

			@Override
			public boolean checkInput(String inputString)
			{
				return true;
			}

			@Override
			public boolean canClose(String inputString)
			{
				return !StringUtil.isEmptyOrSpaces(inputString) && getErrorText(inputString) == null;
			}
		});
	}

	@Nullable
	@Override
	protected Class<? extends ModuleExtension> getModuleExtensionClass()
	{
		return JavaModuleExtension.class;
	}

	@Override
	protected String getErrorTitle()
	{
		return JavaCoreBundle.message("title.cannot.create.class");
	}

	@Override
	protected String getActionName(PsiDirectory directory, String newName, String templateName)
	{
		PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
		assert aPackage != null;
		return JavaCoreBundle.message("progress.creating.class", StringUtil.getQualifiedName(aPackage.getQualifiedName(), newName));
	}

	protected final PsiClass doCreate(PsiDirectory dir, String className, String templateName) throws IncorrectOperationException
	{
		return JavaDirectoryService.getInstance().createClass(dir, className, templateName, true);
	}

	@Override
	protected PsiElement getNavigationElement(@NotNull PsiClass createdElement)
	{
		return createdElement.getLBrace();
	}

	@Override
	protected void postProcess(PsiClass createdElement, String templateName, Map<String, String> customProperties)
	{
		super.postProcess(createdElement, templateName, customProperties);

		moveCaretAfterNameIdentifier(createdElement);
	}
}
