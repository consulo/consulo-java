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

/**
 * Represents the list of name/value elements for an annotation.
 *
 * @author ven
 * @see PsiAnnotation
 */
public interface PsiAnnotationParameterList extends PsiElement {
    /**
     * Returns the array of name/value elements specified on the annotation.
     *
     * @return the array of name/value pairs.
     */
    @Nonnull
    PsiNameValuePair[] getAttributes();
}
