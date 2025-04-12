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
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.function.Predicate;

@ExtensionImpl
public class AnnotatedPackagesSearcher implements AnnotatedPackagesSearchExecutor {
    private static final Logger LOG = Logger.getInstance(AnnotatedPackagesSearcher.class);

    @Override
    @RequiredReadAction
    public boolean execute(@Nonnull AnnotatedPackagesSearch.Parameters p, @Nonnull Predicate<? super PsiJavaPackage> consumer) {
        PsiClass annClass = p.getAnnotationClass();
        assert annClass.isAnnotationType() : "Annotation type should be passed to annotated packages search";

        String annotationFQN = annClass.getQualifiedName();
        assert annotationFQN != null;

        PsiManager psiManager = annClass.getManager();
        SearchScope useScope = p.getScope();

        String annotationShortName = annClass.getName();
        assert annotationShortName != null;

        GlobalSearchScope scope = useScope instanceof GlobalSearchScope globalSearchScope ? globalSearchScope : null;

        Collection<PsiAnnotation> annotations =
            JavaAnnotationIndex.getInstance().get(annotationShortName, psiManager.getProject(), scope);
        for (PsiAnnotation annotation : annotations) {
            PsiModifierList modlist = (PsiModifierList)annotation.getParent();
            if (!(modlist.getParent() instanceof PsiClass candidate && "package-info".equals(candidate.getName()))) {
                continue;
            }

            LOG.assertTrue(candidate.isValid());

            PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
            if (ref == null) {
                continue;
            }

            if (!psiManager.areElementsEquivalent(ref.resolve(), annClass)) {
                continue;
            }
            if (useScope instanceof GlobalSearchScope
                && !useScope.contains(candidate.getContainingFile().getVirtualFile())) {
                continue;
            }
            String qname = candidate.getQualifiedName();
            if (qname != null && !consumer.test(JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(
                qname.substring(0, qname.lastIndexOf('.'))))) {
                return false;
            }
        }

        PsiSearchHelper helper = PsiSearchHelper.SERVICE.getInstance(psiManager.getProject());
        GlobalSearchScope infoFilesFilter = new PackageInfoFilesOnly();

        GlobalSearchScope infoFiles =
            useScope instanceof GlobalSearchScope globalSearchScope ? globalSearchScope.intersectWith(infoFilesFilter) : infoFilesFilter;

        boolean[] wantmore = new boolean[]{true};
        helper.processAllFilesWithWord(
            annotationShortName,
            infoFiles,
            psiFile -> {
                PsiPackageStatement stmt = PsiTreeUtil.getChildOfType(psiFile, PsiPackageStatement.class);
                if (stmt == null) {
                    return true;
                }

                PsiModifierList annotations1 = stmt.getAnnotationList();
                if (annotations1 == null) {
                    return true;
                }
                PsiAnnotation ann = annotations1.findAnnotation(annotationFQN);
                if (ann == null) {
                    return true;
                }

                PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
                if (ref == null) {
                    return true;
                }

                if (!psiManager.areElementsEquivalent(ref.resolve(), annClass)) {
                    return true;
                }

                wantmore[0] = consumer.test(JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(stmt.getPackageName()));
                return wantmore[0];
            },
            true
        );

        return wantmore[0];
    }

    private static class PackageInfoFilesOnly extends GlobalSearchScope {
        @Override
        public int compare(@Nonnull VirtualFile file1, @Nonnull VirtualFile file2) {
            return 0;
        }

        @Override
        public boolean contains(VirtualFile file) {
            return "package-info.java".equals(file.getName());
        }

        @Override
        public boolean isSearchInLibraries() {
            return false;
        }

        @Override
        public boolean isSearchInModuleContent(@Nonnull Module aModule) {
            return true;
        }
    }
}