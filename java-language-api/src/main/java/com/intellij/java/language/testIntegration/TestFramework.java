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

package com.intellij.java.language.testIntegration;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.fileTemplate.FileTemplateDescriptor;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.module.Module;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface TestFramework {
  ExtensionPointName<TestFramework> EXTENSION_NAME = ExtensionPointName.create(TestFramework.class);

  @jakarta.annotation.Nonnull
  String getName();

  @Nonnull
  Image getIcon();

  boolean isLibraryAttached(@jakarta.annotation.Nonnull Module module);

  @Nullable
  String getLibraryPath();

  @Nullable
  String getDefaultSuperClass();

  boolean isTestClass(@jakarta.annotation.Nonnull PsiElement clazz);

  boolean isPotentialTestClass(@Nonnull PsiElement clazz);

  @Nullable
  PsiElement findSetUpMethod(@jakarta.annotation.Nonnull PsiElement clazz);

  @jakarta.annotation.Nullable
  PsiElement findTearDownMethod(@Nonnull PsiElement clazz);

  @jakarta.annotation.Nullable
  PsiElement findOrCreateSetUpMethod(@jakarta.annotation.Nonnull PsiElement clazz) throws IncorrectOperationException;

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

  default boolean isTestMethod(PsiElement element, boolean checkAbstract) {
    return isTestMethod(element);
  }

  @jakarta.annotation.Nonnull
  Language getLanguage();
}
