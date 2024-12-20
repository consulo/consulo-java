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

import consulo.language.psi.NavigatablePsiElement;

import jakarta.annotation.Nullable;

/**
 * Represents a member of a Java class (for example, a field or a method).
 */
public interface PsiMember extends PsiModifierListOwner, NavigatablePsiElement {
    /**
     * The empty array of PSI members which can be reused to avoid unnecessary allocations.
     */
    PsiMember[] EMPTY_ARRAY = new PsiMember[0];

    /**
     * Returns the class containing the member.
     *
     * @return the containing class.
     */
    @Nullable
    PsiClass getContainingClass();

    /**
     * Handy shortcut for checking if this member is abstract.
     */
    default boolean isAbstract() {
        return hasModifierProperty(PsiModifier.ABSTRACT);
    }

    /**
     * Handy shortcut for checking if this member is final.
     */
    default boolean isFinal() {
        return hasModifierProperty(PsiModifier.FINAL);
    }

    /**
     * Handy shortcut for checking if this member is private.
     */
    default boolean isPrivate() {
        return hasModifierProperty(PsiModifier.PRIVATE);
    }

    /**
     * Handy shortcut for checking if this member is protected.
     */
    default boolean isProtected() {
        return hasModifierProperty(PsiModifier.PROTECTED);
    }

    /**
     * Handy shortcut for checking if this member is public.
     */
    default boolean isPublic() {
        return hasModifierProperty(PsiModifier.PUBLIC);
    }

    /**
     * Handy shortcut for checking if this member is static.
     */
    default boolean isStatic() {
        return hasModifierProperty(PsiModifier.STATIC);
    }
}
