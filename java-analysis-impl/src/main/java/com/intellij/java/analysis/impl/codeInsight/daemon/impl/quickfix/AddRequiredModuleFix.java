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
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nls;

import javax.annotation.Nullable;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiJavaModuleReferenceElement;
import com.intellij.java.language.psi.PsiJavaParserFacade;
import com.intellij.java.language.psi.PsiRequiresStatement;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import consulo.java.analysis.impl.JavaQuickFixBundle;

/**
 * @author Pavel.Dolgov
 */
public class AddRequiredModuleFix extends LocalQuickFixAndIntentionActionOnPsiElement
{
	private final String myRequiredName;

	public AddRequiredModuleFix(PsiJavaModule module, String requiredName)
	{
		super(module);
		myRequiredName = requiredName;
	}

	@Nls
	@Nonnull
	@Override
	public String getText()
	{
		return JavaQuickFixBundle.message("module.info.add.requires.name", myRequiredName);
	}

	@Nls
	@Nonnull
	@Override
	public String getFamilyName()
	{
		return JavaQuickFixBundle.message("module.info.add.requires.family.name");
	}

	@Override
	public boolean isAvailable(@Nonnull Project project, @Nonnull PsiFile file, @Nonnull PsiElement startElement, @Nonnull PsiElement endElement)
	{
		return PsiUtil.isLanguageLevel9OrHigher(file) && startElement instanceof PsiJavaModule && startElement.getManager().isInProject(startElement) && getLBrace((PsiJavaModule) startElement) !=
				null;
	}

	@Override
	public void invoke(@Nonnull Project project, @Nonnull PsiFile file, @javax.annotation.Nullable Editor editor, @Nonnull PsiElement startElement, @Nonnull PsiElement endElement)
	{
		PsiJavaModule module = (PsiJavaModule) startElement;

		PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(project).getParserFacade();
		PsiJavaModule tempModule = parserFacade.createModuleFromText("module " + module.getName() + " { requires " + myRequiredName + "; }");
		Iterable<PsiRequiresStatement> tempModuleRequires = tempModule.getRequires();
		PsiRequiresStatement requiresStatement = tempModuleRequires.iterator().next();

		PsiElement addingPlace = findAddingPlace(module);
		if(addingPlace != null)
		{
			addingPlace.getParent().addAfter(requiresStatement, addingPlace);
		}
	}

	@Override
	public boolean startInWriteAction()
	{
		return true;
	}

	@javax.annotation.Nullable
	private static PsiElement findAddingPlace(@Nonnull PsiJavaModule module)
	{
		PsiElement addingPlace = ContainerUtil.iterateAndGetLastItem(module.getRequires());
		return addingPlace != null ? addingPlace : getLBrace(module);
	}

	@Nullable
	private static PsiElement getLBrace(@Nonnull PsiJavaModule module)
	{
		PsiJavaModuleReferenceElement nameElement = module.getNameIdentifier();
		for(PsiElement element = nameElement.getNextSibling(); element != null; element = element.getNextSibling())
		{
			if(PsiUtil.isJavaToken(element, JavaTokenType.LBRACE))
			{
				return element;
			}
		}
		return null; // module-info is incomplete
	}
}
