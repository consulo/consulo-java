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

import com.intellij.java.language.psi.HierarchicalMethodSignature;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearch;
import com.intellij.java.language.psi.search.searches.SuperMethodsSearchExecutor;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author ven
 */
@ExtensionImpl
public class MethodSuperSearcher implements SuperMethodsSearchExecutor {
    private static final Logger LOG = Logger.getInstance(MethodSuperSearcher.class);

    @Override
    public boolean execute(
        @Nonnull SuperMethodsSearch.SearchParameters queryParameters,
        @Nonnull Predicate<? super MethodSignatureBackedByPsiMethod> consumer
    ) {
        PsiClass parentClass = queryParameters.getPsiClass();
        PsiMethod method = queryParameters.getMethod();
        return Application.get().runReadAction((Supplier<Boolean>)() -> {
            HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();

            boolean checkBases = queryParameters.isCheckBases();
            boolean allowStaticMethod = queryParameters.isAllowStaticMethod();
            List<HierarchicalMethodSignature> supers = signature.getSuperSignatures();
            for (HierarchicalMethodSignature superSignature : supers) {
                if (MethodSignatureUtil.isSubsignature(superSignature, signature)
                    && !addSuperMethods(superSignature, method, parentClass, allowStaticMethod, checkBases, consumer)) {
                    return false;
                }
            }

            return true;
        });
    }

    private static boolean addSuperMethods(
        HierarchicalMethodSignature signature,
        PsiMethod method,
        PsiClass parentClass,
        boolean allowStaticMethod,
        boolean checkBases,
        Predicate<? super MethodSignatureBackedByPsiMethod> consumer
    ) {
        PsiMethod signatureMethod = signature.getMethod();
        PsiClass hisClass = signatureMethod.getContainingClass();
        if (parentClass == null || InheritanceUtil.isInheritorOrSelf(parentClass, hisClass, true)) {
            if (isAcceptable(signatureMethod, method, allowStaticMethod)) {
                if (parentClass != null && !parentClass.equals(hisClass) && !checkBases) {
                    return true;
                }
                LOG.assertTrue(signatureMethod != method, method); // method != method.getsuper()
                return consumer.test(signature); //no need to check super classes
            }
        }
        for (HierarchicalMethodSignature superSignature : signature.getSuperSignatures()) {
            if (MethodSignatureUtil.isSubsignature(superSignature, signature)) {
                addSuperMethods(superSignature, method, parentClass, allowStaticMethod, checkBases, consumer);
            }
        }

        return true;
    }

    private static boolean isAcceptable(PsiMethod superMethod, PsiMethod method, boolean allowStaticMethod) {
        boolean hisStatic = superMethod.isStatic();
        return hisStatic == method.isStatic() && (allowStaticMethod || !hisStatic)
            && JavaPsiFacade.getInstance(method.getProject()).getResolveHelper().isAccessible(superMethod, method, null);
    }
}
