// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.cache;

import com.intellij.java.language.psi.TypeAnnotationProvider;
import consulo.language.psi.PsiElement;

/**
 * A container of all type annotations for given type, including structural children of the type
 * (type arguments, wildcard bounds, outer types, array element types)
 */
public interface TypeAnnotationContainer {
    /**
     * A container that contains no type annotations.
     */
    TypeAnnotationContainer EMPTY = new TypeAnnotationContainer() {
        @Override
        public TypeAnnotationContainer forArrayElement() {
            return this;
        }

        @Override
        public TypeAnnotationContainer forEnclosingClass() {
            return this;
        }

        @Override
        public TypeAnnotationContainer forBound() {
            return this;
        }

        @Override
        public TypeAnnotationContainer forTypeArgument(int index) {
            return this;
        }

        @Override
        public TypeAnnotationProvider getProvider(PsiElement parent) {
            return TypeAnnotationProvider.EMPTY;
        }

        @Override
        public void appendImmediateText(StringBuilder sb) {
        }
    };

    /**
     * @return a derived container that contains annotations for an array element,
     * assuming that this container is used for the array
     */
    TypeAnnotationContainer forArrayElement();

    /**
     * @return a derived container that contains annotations for enclosing class,
     * assuming that this container is used for the inner class
     */
    TypeAnnotationContainer forEnclosingClass();

    /**
     * @return type annotation container for wildcard bound
     * (assuming that this type annotation container is used for the bounded wildcard type)
     */
    TypeAnnotationContainer forBound();

    /**
     * Returns a type annotation container for the given type argument index.
     * This is used for types that have type arguments, and it provides the
     * annotations associated with a specific type argument.
     *
     * @param index type argument index, zero-based
     * @return type annotation container for given type argument
     * (assuming that this type annotation container is used for a class type with type arguments)
     */
    TypeAnnotationContainer forTypeArgument(int index);

    /**
     * Returns a type annotation container for the given conjunction index.
     * Used for type parameters.
     *
     * @param i type argument index, zero-based
     * @return type annotation container for a given type argument
     * (assuming that this type annotation container is used for a class type with type arguments)
     */
    default TypeAnnotationContainer forConjunction(int i) {
        return forTypeArgument(i);
    }

    /**
     * @param parent parent PSI element for context
     * @return TypeAnnotationProvider
     */
    TypeAnnotationProvider getProvider(PsiElement parent);

    /**
     * Appends to StringBuilder annotation text that applicable to this element immediately (not to sub-elements)
     */
    void appendImmediateText(StringBuilder sb);
}
