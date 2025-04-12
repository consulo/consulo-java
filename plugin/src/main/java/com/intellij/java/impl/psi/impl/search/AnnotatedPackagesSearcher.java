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

/*
 * @author max
 */
package com.intellij.java.impl.psi.impl.search;

import com.intellij.java.impl.psi.search.searches.AnnotatedPackagesSearch;
import com.intellij.java.impl.psi.search.searches.AnnotatedPackagesSearchExecutor;
import com.intellij.java.indexing.impl.stubs.index.JavaAnnotationIndex;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;

import java.util.Collection;

@ExtensionImpl
public class AnnotatedPackagesSearcher implements AnnotatedPackagesSearchExecutor {
    private static final Logger LOG = Logger.getInstance(AnnotatedPackagesSearcher.class);

    @Override
    public boolean execute(@Nonnull final AnnotatedPackagesSearch.Parameters p, @Nonnull final Processor<? super PsiJavaPackage> consumer) {
        final PsiClass annClass = p.getAnnotationClass();
        assert annClass.isAnnotationType() : "Annotation type should be passed to annotated packages search";

        final String annotationFQN = annClass.getQualifiedName();
        assert annotationFQN != null;

        final PsiManager psiManager = annClass.getManager();
        final SearchScope useScope = p.getScope();

        final String annotationShortName = annClass.getName();
        assert annotationShortName != null;

        final GlobalSearchScope scope = useScope instanceof GlobalSearchScope ? (GlobalSearchScope)useScope : null;

        final Collection<PsiAnnotation> annotations =
            JavaAnnotationIndex.getInstance().get(annotationShortName, psiManager.getProject(), scope);
        for (PsiAnnotation annotation : annotations) {
            PsiModifierList modlist = (PsiModifierList)annotation.getParent();
            final PsiElement owner = modlist.getParent();
            if (!(owner instanceof PsiClass)) {
                continue;
            }
            PsiClass candidate = (PsiClass)owner;
            if (!"package-info".equals(candidate.getName())) {
                continue;
            }

            LOG.assertTrue(candidate.isValid());

            final PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
            if (ref == null) {
                continue;
            }

            if (!psiManager.areElementsEquivalent(ref.resolve(), annClass)) {
                continue;
            }
            if (useScope instanceof GlobalSearchScope &&
                !((GlobalSearchScope)useScope).contains(candidate.getContainingFile().getVirtualFile())) {
                continue;
            }
            final String qname = candidate.getQualifiedName();
            if (qname != null && !consumer.process(JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(
                qname.substring(0, qname.lastIndexOf('.'))))) {
                return false;
            }
        }

        PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(psiManager.getProject());
        final GlobalSearchScope infoFilesFilter = new PackageInfoFilesOnly();

        GlobalSearchScope infoFiles =
            useScope instanceof GlobalSearchScope ? ((GlobalSearchScope)useScope).intersectWith(infoFilesFilter) : infoFilesFilter;

        final boolean[] wantmore = new boolean[]{true};
        helper.processAllFilesWithWord(annotationShortName, infoFiles, new Processor<PsiFile>() {
                @Override
                public boolean process(final PsiFile psiFile) {
                    PsiPackageStatement stmt = PsiTreeUtil.getChildOfType(psiFile, PsiPackageStatement.class);
                    if (stmt == null) {
                        return true;
                    }

                    final PsiModifierList annotations = stmt.getAnnotationList();
                    if (annotations == null) {
                        return true;
                    }
                    final PsiAnnotation ann = annotations.findAnnotation(annotationFQN);
                    if (ann == null) {
                        return true;
                    }

                    final PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
                    if (ref == null) {
                        return true;
                    }

                    if (!psiManager.areElementsEquivalent(ref.resolve(), annClass)) {
                        return true;
                    }

                    wantmore[0] = consumer.process(JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(stmt.getPackageName()));
                    return wantmore[0];
                }
            },
            true
        );

        return wantmore[0];
    }

    private static class PackageInfoFilesOnly extends GlobalSearchScope {
        @Override
        public int compare(final VirtualFile file1, final VirtualFile file2) {
            return 0;
        }

        @Override
        public boolean contains(final VirtualFile file) {
            return "package-info.java".equals(file.getName());
        }

        @Override
        public boolean isSearchInLibraries() {
            return false;
        }

        @Override
        public boolean isSearchInModuleContent(@Nonnull final Module aModule) {
            return true;
        }
    }
}