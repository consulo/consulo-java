// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.java.language.psi.PsiTypeParameterListOwner;
import consulo.language.psi.PsiElementVisitor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class LightTypeParameterBuilder extends LightPsiClassBuilder implements PsiTypeParameter {

    private final PsiTypeParameterListOwner myOwner;
    private final int myIndex;

    public LightTypeParameterBuilder(@Nonnull String name, PsiTypeParameterListOwner owner, int index) {
        super(owner, name);
        myOwner = owner;
        myIndex = index;
    }

    @Override
    public void accept(@Nonnull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitTypeParameter(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Nullable
    @Override
    public PsiTypeParameterListOwner getOwner() {
        return myOwner;
    }

    @Override
    public int getIndex() {
        return myIndex;
    }

    @Override
    @Nonnull
    public PsiAnnotation[] getAnnotations() {
        return getModifierList().getAnnotations();
    }

    @Override
    @Nonnull
    public PsiAnnotation[] getApplicableAnnotations() {
        return getModifierList().getApplicableAnnotations();
    }

    @Nullable
    @Override
    public PsiAnnotation findAnnotation(@Nonnull String qualifiedName) {
        return getModifierList().findAnnotation(qualifiedName);
    }

    @Nonnull
    @Override
    public PsiAnnotation addAnnotation(@Nonnull String qualifiedName) {
        return getModifierList().addAnnotation(qualifiedName);
    }
}
