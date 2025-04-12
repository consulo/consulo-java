/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.java.indexing.impl.stubs.index.JavaAnnotationIndex;
import com.intellij.java.indexing.search.searches.AnnotatedElementsSearch;
import com.intellij.java.indexing.search.searches.AnnotatedElementsSearchExecutor;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author max
 */
@ExtensionImpl
public class AnnotatedElementsSearcher implements AnnotatedElementsSearchExecutor {
    @Override
    public boolean execute(
        @Nonnull AnnotatedElementsSearch.Parameters p,
        @Nonnull Predicate<? super PsiModifierListOwner> consumer
    ) {
        PsiClass annClass = p.getAnnotationClass();
        assert annClass.isAnnotationType() : "Annotation type should be passed to annotated members search";

        Application app = Application.get();
        String annotationFQN = app.runReadAction((Supplier<String>)annClass::getQualifiedName);
        assert annotationFQN != null;

        PsiManager psiManager = app.runReadAction((Supplier<PsiManager>)annClass::getManager);

        SearchScope useScope = p.getScope();
        Class<? extends PsiModifierListOwner>[] types = p.getTypes();

        for (PsiAnnotation ann : getAnnotationCandidates(annClass, useScope)) {
            PsiModifierListOwner candidate =
                app.runReadAction((Supplier<PsiModifierListOwner>)() -> {
                    PsiElement parent = ann.getParent();
                    if (!(parent instanceof PsiModifierList)) {
                        return null; // Can be a PsiNameValuePair, if annotation is used to annotate annotation parameters
                    }

                    PsiElement owner = parent.getParent();
                    if (!isInstanceof(owner, types)) {
                        return null;
                    }

                    PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
                    if (ref == null || !psiManager.areElementsEquivalent(ref.resolve(), annClass)) {
                        return null;
                    }

                    return (PsiModifierListOwner)owner;
                });

            if (candidate != null && !consumer.test(candidate)) {
                return false;
            }
        }

        return true;
    }

    private static Collection<PsiAnnotation> getAnnotationCandidates(PsiClass annClass, SearchScope useScope) {
        return Application.get().runReadAction((Supplier<Collection<PsiAnnotation>>)() -> {
            if (useScope instanceof GlobalSearchScope globalSearchScope) {
                return JavaAnnotationIndex.getInstance().get(annClass.getName(), annClass.getProject(), globalSearchScope);
            }

            List<PsiAnnotation> result = new ArrayList<>();
            for (PsiElement element : ((LocalSearchScope)useScope).getScope()) {
                result.addAll(PsiTreeUtil.findChildrenOfType(element, PsiAnnotation.class));
            }
            return result;
        });
    }

    public static boolean isInstanceof(PsiElement owner, Class<? extends PsiModifierListOwner>[] types) {
        for (Class<? extends PsiModifierListOwner> type : types) {
            if (type.isInstance(owner)) {
                return true;
            }
        }
        return false;
    }
}
