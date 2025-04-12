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

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.search.searches.DeepestSuperMethodsSearchExecutor;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;

import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
@ExtensionImpl
public class MethodDeepestSuperSearcher implements DeepestSuperMethodsSearchExecutor {
    @Override
    public boolean execute(@Nonnull PsiMethod method, @Nonnull Processor<? super PsiMethod> consumer) {
        return processDeepestSuperMethods(method, consumer);
    }

    public static boolean processDeepestSuperMethods(PsiMethod method, Processor<? super PsiMethod> consumer) {
        final Set<PsiMethod> methods = new HashSet<PsiMethod>();
        methods.add(method);
        return findDeepestSuperOrSelfSignature(method, methods, null, consumer);
    }

    private static boolean findDeepestSuperOrSelfSignature(
        final PsiMethod method,
        Set<PsiMethod> set,
        Set<PsiMethod> guard,
        Processor<? super PsiMethod> processor
    ) {
        if (guard != null && !guard.add(method)) {
            return true;
        }
        PsiMethod[] supers = ApplicationManager.getApplication().runReadAction(new Computable<PsiMethod[]>() {
            @Override
            public PsiMethod[] compute() {
                return method.findSuperMethods();
            }
        });

        if (supers.length == 0 && set.add(method) && !processor.process(method)) {
            return false;
        }
        for (PsiMethod superMethod : supers) {
            if (guard == null) {
                guard = new HashSet<PsiMethod>();
                guard.add(method);
            }
            if (!findDeepestSuperOrSelfSignature(superMethod, set, guard, processor)) {
                return false;
            }
        }
        return true;
    }
}
