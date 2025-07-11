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
package com.intellij.java.language.psi;

import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents the call of a Java method or constructor or a Java enum constant..
 *
 * @author ven
 */
public interface PsiCall extends PsiElement {
    /**
     * Returns the list of arguments passed to the called method.
     *
     * @return the argument list, or null if the call is incomplete.
     */
    @Nullable
    PsiExpressionList getArgumentList();

    /**
     * Resolves the reference to the called method and returns the method.
     *
     * @return the called method, or null if the resolve failed.
     */
    @Nullable
    PsiMethod resolveMethod();

    /**
     * Resolves the reference to the called method and returns the resolve result
     * containing the method and the substitutor for generic type parameters.
     *
     * @return the resolve result, or {@link JavaResolveResult#EMPTY} if unresolved
     */
    @Nonnull
    JavaResolveResult resolveMethodGenerics();


    /**
     * Returns the results of resolving the called method.
     *
     * @param incompleteCode if true, the code in the context of which the call is
     *                       being resolved is considered incomplete, and the method may return additional
     *                       invalid results.
     * @return the array of results for resolving the called method.
     */
    @Nonnull
    default JavaResolveResult[] multiResolve(boolean incompleteCode) {
        JavaResolveResult result = resolveMethodGenerics();
        return result == JavaResolveResult.EMPTY ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[]{result};
    }
}
