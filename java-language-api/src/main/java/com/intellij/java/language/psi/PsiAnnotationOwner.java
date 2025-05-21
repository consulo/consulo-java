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
package com.intellij.java.language.psi;

import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

/**
 * @author cdr
 */
public interface PsiAnnotationOwner {
    /**
     * Returns the list of annotations syntactically contained in the element.
     *
     * @return the list of annotations.
     */
    @Nonnull
    PsiAnnotation[] getAnnotations();

    /**
     * @return the list of annotations which are applicable to this owner
     * (e.g. type annotations on method belong to its type element, not the method).
     */
    @Nonnull
    PsiAnnotation[] getApplicableAnnotations();

    /**
     * Searches the owner for an annotation with the specified fully qualified name
     * and returns {@code true} if it is found.
     * <p/>
     * This method is preferable over {@link #findAnnotation}
     * since implementations are free not to instantiate the {@link PsiAnnotation}.
     *
     * @param qualifiedName the fully qualified name of the annotation to find
     * @return {@code true} is such annotation is found, otherwise {@code false}
     */
    default boolean hasAnnotation(@Nonnull @NonNls String qualifiedName) {
        //noinspection SSBasedInspection
        return findAnnotation(qualifiedName) != null;
    }

    /**
     * Searches the owner for an annotation with the specified fully qualified name
     * and returns one if it is found.
     *
     * @param qualifiedName the fully qualified name of the annotation to find.
     * @return the annotation instance, or null if no such annotation is found.
     */
    @Nullable
    PsiAnnotation findAnnotation(@Nonnull @NonNls String qualifiedName);

    /**
     * Adds a new annotation to this owner. The annotation class name will be shortened. No attributes will be defined.
     *
     * @param qualifiedName qualifiedName
     * @return newly added annotation
     */
    @Nonnull
    PsiAnnotation addAnnotation(@Nonnull @NonNls String qualifiedName);
}
