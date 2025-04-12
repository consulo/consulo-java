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
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
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

import java.util.function.Predicate;

@ExtensionImpl
public class SPIReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> implements ReferencesSearchQueryExecutor {
    public SPIReferencesSearcher() {
        super(true);
    }

    @Override
    @RequiredReadAction
    public void processQuery(@Nonnull ReferencesSearch.SearchParameters p, @Nonnull Predicate<? super PsiReference> consumer) {
        PsiElement element = p.getElementToSearch();
        if (!element.isValid()) {
            return;
        }

        if (!(p.getEffectiveSearchScope() instanceof GlobalSearchScope globalSearchScope)) {
            return;
        }

        if (element instanceof PsiClass aClass) {
            String jvmClassName = ClassUtil.getJVMClassName(aClass);

            if (jvmClassName == null) {
                return;
            }
            PsiFile[] files = FilenameIndex.getFilesByName(aClass.getProject(), jvmClassName, globalSearchScope);
            for (PsiFile file : files) {
                if (file.getLanguage() == SPILanguage.INSTANCE) {
                    PsiReference reference = file.getReference();
                    if (reference != null) {
                        consumer.test(reference);
                    }
                }
            }
        }
        else if (element instanceof PsiPackage aPackage) {
            String qualifiedName = aPackage.getQualifiedName();
            Project project = aPackage.getProject();
            String[] filenames = FilenameIndex.getAllFilenames(project);
            for (String filename : filenames) {
                if (filename.startsWith(qualifiedName + ".")) {
                    for (PsiFile file : FilenameIndex.getFilesByName(project, filename, globalSearchScope)) {
                        if (file.getLanguage() == SPILanguage.INSTANCE) {
                            for (PsiReference reference : file.getReferences()) {
                                if (reference.getCanonicalText().equals(qualifiedName)) {
                                    consumer.test(reference);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
