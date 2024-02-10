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

package com.intellij.java.impl.util.xml.converters.values;

import com.intellij.java.impl.util.xml.DomJavaUtil;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.util.xml.ConvertContext;
import consulo.xml.util.xml.Converter;
import consulo.xml.util.xml.CustomReferenceConverter;
import consulo.xml.util.xml.GenericDomValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;

@ServiceAPI(ComponentScope.APPLICATION)
public abstract class ClassValueConverter extends Converter<PsiClass> implements CustomReferenceConverter {

  public static ClassValueConverter getClassValueConverter() {
    return ServiceManager.getService(ClassValueConverter.class);
  }

  public PsiClass fromString(@Nullable @NonNls String s, final ConvertContext context) {
    if (s == null) return null;
    final Module module = context.getModule();
    final PsiFile psiFile = context.getFile();
    final Project project = psiFile.getProject();
    return DomJavaUtil.findClass(s, context.getFile(), context.getModule(), getScope(project, module, psiFile));
  }

  public String toString(@Nullable PsiClass psiClass, final ConvertContext context) {
    return psiClass == null ? null : psiClass.getQualifiedName();
  }

  @Nonnull
  public abstract PsiReference[] createReferences(GenericDomValue genericDomValue, PsiElement element, ConvertContext context);

  public static GlobalSearchScope getScope(Project project, @Nullable Module module, @Nullable PsiFile psiFile) {
    if (module == null || psiFile == null) {
      return (GlobalSearchScope) ProjectScopes.getAllScope(project);
    }
    VirtualFile file = psiFile.getOriginalFile().getVirtualFile();
    if (file == null) {
      return (GlobalSearchScope) ProjectScopes.getAllScope(project);
    }
    final boolean inTests = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(file);

    return GlobalSearchScope.moduleRuntimeScope(module, inTests);
  }
}
