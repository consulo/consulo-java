/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.testIntegration;

import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface TestFramework
{
	ExtensionPointName<TestFramework> EXTENSION_NAME = ExtensionPointName.create("consulo.java.testFramework");

	@Nonnull
	String getName();

	@Nonnull
	Image getIcon();

	boolean isLibraryAttached(@Nonnull Module module);

	@Nullable
	String getLibraryPath();

	@Nullable
	String getDefaultSuperClass();

	boolean isTestClass(@Nonnull PsiElement clazz);

	boolean isPotentialTestClass(@Nonnull PsiElement clazz);

	@Nullable
	PsiElement findSetUpMethod(@Nonnull PsiElement clazz);

	@Nullable
	PsiElement findTearDownMethod(@Nonnull PsiElement clazz);

	@Nullable
	PsiElement findOrCreateSetUpMethod(@Nonnull PsiElement clazz) throws IncorrectOperationException;

	FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor();

	FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor();

	@Nonnull
	FileTemplateDescriptor getTestMethodFileTemplateDescriptor();

	/**
	 * should be checked for abstract method error
	 */
	boolean isIgnoredMethod(PsiElement element);

	/**
	 * should be checked for abstract method error
	 */
	boolean isTestMethod(PsiElement element);

	default boolean isTestMethod(PsiElement element, boolean checkAbstract)
	{
		return isTestMethod(element);
	}

	@Nonnull
	Language getLanguage();
}
