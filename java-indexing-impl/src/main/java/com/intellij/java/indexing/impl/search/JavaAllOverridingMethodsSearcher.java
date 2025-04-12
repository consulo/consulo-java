/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.java.indexing.search.searches.AllOverridingMethodsSearch;
import com.intellij.java.indexing.search.searches.AllOverridingMethodsSearchExecutor;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.content.scope.SearchScope;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author ven
 */
@ExtensionImpl
public class JavaAllOverridingMethodsSearcher implements AllOverridingMethodsSearchExecutor {
    @Override
    public boolean execute(
        @Nonnull AllOverridingMethodsSearch.SearchParameters p,
        @Nonnull Predicate<? super Pair<PsiMethod, PsiMethod>> consumer
    ) {
        PsiClass psiClass = p.getPsiClass();

        MultiMap<String, PsiMethod> methods =
            Application.get().runReadAction((Supplier<MultiMap<String, PsiMethod>>)() -> {
                MultiMap<String, PsiMethod> methods1 = MultiMap.create();
                for (PsiMethod method : psiClass.getMethods()) {
                    if (PsiUtil.canBeOverriden(method)) {
                        methods1.putValue(method.getName(), method);
                    }
                }
                return methods1;
            });


        SearchScope scope = p.getScope();

        Predicate<PsiClass> inheritorsProcessor = inheritor -> {
            PsiSubstitutor substitutor = null;

            for (String name : methods.keySet()) {
                if (inheritor.findMethodsByName(name, true).length == 0) {
                    continue;
                }

                for (PsiMethod method : methods.get(name)) {
                    if (method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && !JavaPsiFacade.getInstance(inheritor.getProject())
                        .arePackagesTheSame(psiClass, inheritor)) {
                        continue;
                    }

                    if (substitutor == null) {
                        //could be null if not java inheritor, TODO only JavaClassInheritors are needed
                        substitutor = TypeConversionUtil.getClassSubstitutor(psiClass, inheritor, PsiSubstitutor.EMPTY);
                        if (substitutor == null) {
                            return true;
                        }
                    }

                    MethodSignature signature = method.getSignature(substitutor);
                    PsiMethod inInheritor = MethodSignatureUtil.findMethodBySuperSignature(inheritor, signature, false);
                    if (inInheritor != null && !inInheritor.isStatic()) {
                        if (!consumer.test(Couple.of(method, inInheritor))) {
                            return false;
                        }
                    }

                    if (psiClass.isInterface() && !inheritor.isInterface()) {  //check for sibling implementation
                        PsiClass superClass = inheritor.getSuperClass();
                        if (superClass != null && !superClass.isInheritor(psiClass, true)) {
                            inInheritor =
                                MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived(inheritor, superClass, signature, true);
                            if (inInheritor != null && !inInheritor.isStatic() && !consumer.test(Couple.of(method, inInheritor))) {
                                return false;
                            }
                        }
                    }
                }
            }

            return true;
        };

        return ClassInheritorsSearch.search(psiClass, scope, true).forEach(inheritorsProcessor);
    }
}
