/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiTypeParameter;
import com.intellij.java.language.psi.PsiTypeParameterListOwner;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;

public class LightTypeParameter extends LightClass implements PsiTypeParameter {
    public LightTypeParameter(PsiTypeParameter delegate) {
        super(delegate);
    }

    @Override
    public PsiTypeParameter getDelegate() {
        return (PsiTypeParameter)super.getDelegate();
    }

    @Override
    public PsiElement copy() {
        return new LightTypeParameter(getDelegate());
    }

    @Override
    public void accept(PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor elementVisitor) {
            elementVisitor.visitTypeParameter(this);
        }
        else {
            super.accept(visitor);
        }
    }

    @Override
    public PsiTypeParameterListOwner getOwner() {
        return getDelegate().getOwner();
    }

    @Override
    public int getIndex() {
        return getDelegate().getIndex();
    }

    @Override
    public PsiAnnotation[] getAnnotations() {
        return getDelegate().getAnnotations();
    }

    @Override
    public PsiAnnotation[] getApplicableAnnotations() {
        return getDelegate().getApplicableAnnotations();
    }

    @Override
    public PsiAnnotation findAnnotation(String qualifiedName) {
        return getDelegate().findAnnotation(qualifiedName);
    }

    @Override
    public PsiAnnotation addAnnotation(String qualifiedName) {
        return getDelegate().addAnnotation(qualifiedName);
    }

    public boolean useDelegateToSubstitute() {
        return true;
    }

    @Override
    @RequiredReadAction
    public String toString() {
        return "PsiTypeParameter:" + getName();
    }
}
