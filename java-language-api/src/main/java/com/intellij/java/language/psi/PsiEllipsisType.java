// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi;

import com.intellij.java.language.codeInsight.TypeNullability;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

/**
 * Represents the type of a variable arguments array passed as a method parameter.
 */
public class PsiEllipsisType extends PsiArrayType {
    public PsiEllipsisType(PsiType componentType) {
        super(componentType);
    }

    public PsiEllipsisType(PsiType componentType, PsiAnnotation[] annotations) {
        super(componentType, annotations);
    }

    public PsiEllipsisType(PsiType componentType, TypeAnnotationProvider provider) {
        super(componentType, provider);
    }

    private PsiEllipsisType(PsiType componentType,
                            TypeAnnotationProvider provider,
                            @Nullable TypeNullability nullability,
                            @Nullable PsiModifierListOwner containerNullabilityOwner) {
        super(componentType, provider, nullability, containerNullabilityOwner);
    }

    @Override
    public String getPresentableText(boolean annotated) {
        return getText(getDeepComponentType().getPresentableText(annotated), "...", false, annotated);
    }

    @Override
    public String getCanonicalText(boolean annotated) {
        return getText(getDeepComponentType().getCanonicalText(annotated), "...", true, annotated);
    }

    @Override
    public String getInternalCanonicalText() {
        return getText(getDeepComponentType().getInternalCanonicalText(), "...", true, true);
    }

    @Override
    public boolean equalsToText(String text) {
        return text.endsWith("...") && getComponentType().equalsToText(text.substring(0, text.length() - 3)) ||
            super.equalsToText(text);
    }

    @Override
    public PsiType withContainerNullability(@Nullable PsiModifierListOwner containerNullabilityContext) {
        if (containerNullabilityContext == myContainerNullabilityContext) {
            return this;
        }
        return new PsiEllipsisType(getComponentType(), getAnnotationProvider(), myNullability, containerNullabilityContext);
    }

    @Override
    public PsiType withContainerNullability(@Nullable PsiArrayType arrayType) {
        if (arrayType == null && myContainerNullabilityContext == null) {
            return this;
        }
        if (arrayType != null && arrayType.myContainerNullabilityContext == myContainerNullabilityContext) {
            return this;
        }
        return new PsiEllipsisType(getComponentType(), getAnnotationProvider(), myNullability,
            arrayType != null ? arrayType.myContainerNullabilityContext : null);
    }

    @Override
    public PsiEllipsisType withNullability(TypeNullability nullability) {
        return new PsiEllipsisType(getComponentType(), getAnnotationProvider(), nullability, this.myContainerNullabilityContext);
    }

    /**
     * Converts the ellipsis type to an array type with the same component type.
     *
     * @return the array type instance.
     */
    @Contract(pure = true)
    public PsiType toArrayType() {
        return new PsiArrayType(getComponentType(), getAnnotationProvider());
    }

    @Override
    public <A> A accept(PsiTypeVisitor<A> visitor) {
        return visitor.visitEllipsisType(this);
    }

    @Override
    public int hashCode() {
        return super.hashCode() * 5;
    }
}