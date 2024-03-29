/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.indexing.impl.search;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.util.ClassUtil;
import com.intellij.java.language.spi.SPILanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FilenameIndex;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.ReferencesSearchQueryExecutor;
import consulo.project.Project;
import consulo.project.util.query.QueryExecutorBase;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class SPIReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> implements ReferencesSearchQueryExecutor {
  public SPIReferencesSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@Nonnull final ReferencesSearch.SearchParameters p, @Nonnull final Processor<? super PsiReference> consumer) {
    final PsiElement element = p.getElementToSearch();
    if (!element.isValid()) {
      return;
    }

    final SearchScope scope = p.getEffectiveSearchScope();
    if (!(scope instanceof GlobalSearchScope)) {
      return;
    }

    if (element instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)element;
      final String jvmClassName = ClassUtil.getJVMClassName(aClass);

      if (jvmClassName == null) {
        return;
      }
      final PsiFile[] files = FilenameIndex.getFilesByName(aClass.getProject(), jvmClassName, (GlobalSearchScope)scope);
      for (PsiFile file : files) {
        if (file.getLanguage() == SPILanguage.INSTANCE) {
          final PsiReference reference = file.getReference();
          if (reference != null) {
            consumer.process(reference);
          }
        }
      }
    }
    else if (element instanceof PsiPackage) {
      final String qualifiedName = ((PsiPackage)element).getQualifiedName();
      final Project project = element.getProject();
      final String[] filenames = FilenameIndex.getAllFilenames(project);
      for (final String filename : filenames) {
        if (filename.startsWith(qualifiedName + ".")) {
          final PsiFile[] files = FilenameIndex.getFilesByName(project, filename, (GlobalSearchScope)scope);
          for (PsiFile file : files) {
            if (file.getLanguage() == SPILanguage.INSTANCE) {
              final PsiReference[] references = file.getReferences();
              for (final PsiReference reference : references) {
                if (reference.getCanonicalText().equals(qualifiedName)) {
                  consumer.process(reference);
                }
              }
            }
          }
        }
      }
    }
  }
}
