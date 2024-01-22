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

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.projectRoots.roots.ExternalLibraryDescriptor;
import com.intellij.java.language.psi.JVMElementFactory;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.execution.configuration.ConfigurationType;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateDescriptor;
import consulo.fileTemplate.FileTemplateManager;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class JavaTestFramework implements TestFramework {
  @Override
  public boolean isLibraryAttached(@jakarta.annotation.Nonnull consulo.module.Module module) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    PsiClass c = JavaPsiFacade.getInstance(module.getProject()).findClass(getMarkerClassFQName(), scope);
    return c != null;
  }

  @Nullable
  @Override
  public String getLibraryPath() {
    ExternalLibraryDescriptor descriptor = getFrameworkLibraryDescriptor();
    if (descriptor != null) {
      return descriptor.getLibraryClassesRoots().get(0);
    }
    return null;
  }

  public ExternalLibraryDescriptor getFrameworkLibraryDescriptor() {
    return null;
  }

  protected abstract String getMarkerClassFQName();

  @Override
  public boolean isTestClass(@Nonnull PsiElement clazz) {
    return clazz instanceof PsiClass && isTestClass((PsiClass)clazz, false);
  }

  @Override
  public boolean isPotentialTestClass(@jakarta.annotation.Nonnull PsiElement clazz) {
    return clazz instanceof PsiClass && isTestClass((PsiClass)clazz, true);
  }

  protected abstract boolean isTestClass(PsiClass clazz, boolean canBePotential);

  protected boolean isUnderTestSources(PsiClass clazz) {
    PsiFile psiFile = clazz.getContainingFile();
    VirtualFile vFile = psiFile.getVirtualFile();
    if (vFile == null) {
      return false;
    }
    return ProjectRootManager.getInstance(clazz.getProject()).getFileIndex().isInTestSourceContent(vFile);
  }

  @Override
  @jakarta.annotation.Nullable
  public PsiElement findSetUpMethod(@Nonnull PsiElement clazz) {
    return clazz instanceof PsiClass ? findSetUpMethod((PsiClass)clazz) : null;
  }

  @Nullable
  protected abstract PsiMethod findSetUpMethod(@Nonnull PsiClass clazz);

  @Override
  @jakarta.annotation.Nullable
  public PsiElement findTearDownMethod(@jakarta.annotation.Nonnull PsiElement clazz) {
    return clazz instanceof PsiClass ? findTearDownMethod((PsiClass)clazz) : null;
  }

  @jakarta.annotation.Nullable
  protected abstract PsiMethod findTearDownMethod(@Nonnull PsiClass clazz);

  @Override
  public PsiElement findOrCreateSetUpMethod(@jakarta.annotation.Nonnull PsiElement clazz) throws IncorrectOperationException {
    return clazz instanceof PsiClass ? findOrCreateSetUpMethod((PsiClass)clazz) : null;
  }

  @Override
  public boolean isIgnoredMethod(PsiElement element) {
    return false;
  }

  @Override
  @jakarta.annotation.Nonnull
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @jakarta.annotation.Nullable
  protected abstract PsiMethod findOrCreateSetUpMethod(PsiClass clazz) throws IncorrectOperationException;

  public boolean isParameterized(PsiClass clazz) {
    return false;
  }

  @Nullable
  public PsiMethod findParametersMethod(PsiClass clazz) {
    return null;
  }

  @Nullable
  public FileTemplateDescriptor getParametersMethodFileTemplateDescriptor() {
    return null;
  }

  public abstract char getMnemonic();

  public PsiMethod createSetUpPatternMethod(JVMElementFactory factory) {
    final FileTemplate template =
      FileTemplateManager.getDefaultInstance().getCodeTemplate(getSetUpMethodFileTemplateDescriptor().getFileName());
    final String templateText = StringUtil.replace(StringUtil.replace(template.getText(), "${BODY}\n", ""), "${NAME}", "setUp");
    return factory.createMethodFromText(templateText, null);
  }

  public FileTemplateDescriptor getTestClassFileTemplateDescriptor() {
    return null;
  }

  public void setupLibrary(Module module) {
    /*ExternalLibraryDescriptor descriptor = getFrameworkLibraryDescriptor();
		if(descriptor != null)
		{
			JavaProjectModelModificationService.getInstance(module.getProject()).addDependency(module, descriptor, DependencyScope.TEST);
		}
		else
		{
			String path = getLibraryPath();
			if(path != null)
			{
				OrderEntryFix.addJarsToRoots(Collections.singletonList(path), null, module, null);
			}
		}  */
  }

  public boolean isSingleConfig() {
    return false;
  }

  /**
   * @return true for junit 3 classes with suite method and for junit 4 tests with @Suite annotation
   */
  public boolean isSuiteClass(PsiClass psiClass) {
    return false;
  }

  public boolean isTestMethod(PsiMethod method, PsiClass myClass) {
    return isTestMethod(method);
  }

  public boolean acceptNestedClasses() {
    return false;
  }

  @Override
  public boolean isTestMethod(PsiElement element) {
    return isTestMethod(element, true);
  }

  public boolean isMyConfigurationType(ConfigurationType type) {
    return false;
  }
}
