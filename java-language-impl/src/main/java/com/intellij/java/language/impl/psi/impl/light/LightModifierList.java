/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.language.Language;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;

public class LightModifierList extends LightElement implements PsiModifierList {
    private final Set<String> myModifiers;

    public LightModifierList(PsiModifierListOwner modifierListOwner) {
        this(modifierListOwner.getManager());
        copyModifiers(modifierListOwner.getModifierList());
    }

    public LightModifierList(PsiManager manager) {
        this(manager, JavaLanguage.INSTANCE);
    }

    public LightModifierList(PsiManager manager, final Language language, String... modifiers) {
        super(manager, language);
        myModifiers = new HashSet<>(Set.of(modifiers));
    }

    public void addModifier(String modifier) {
        myModifiers.add(modifier);
    }

    public void copyModifiers(PsiModifierList modifierList) {
        if (modifierList == null) {
            return;
        }
        for (String modifier : PsiModifier.MODIFIERS) {
            if (modifierList.hasExplicitModifier(modifier)) {
                addModifier(modifier);
            }
        }
    }

    public void clearModifiers() {
        myModifiers.clear();
    }

    @Override
    public boolean hasModifierProperty(@Nonnull String name) {
        return myModifiers.contains(name);
    }

    @Override
    public boolean hasExplicitModifier(@Nonnull String name) {
        return myModifiers.contains(name);
    }

    @Override
    public void setModifierProperty(@Nonnull String name, boolean value) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public void checkSetModifierProperty(@Nonnull String name, boolean value) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    @Nonnull
    public PsiAnnotation[] getAnnotations() {
        //todo
        return PsiAnnotation.EMPTY_ARRAY;
    }

    @Override
    @Nonnull
    public PsiAnnotation[] getApplicableAnnotations() {
        return getAnnotations();
    }

    @Override
    public PsiAnnotation findAnnotation(@Nonnull String qualifiedName) {
        return null;
    }

    @Override
    @Nonnull
    public PsiAnnotation addAnnotation(@Nonnull @NonNls String qualifiedName) {
        throw new IncorrectOperationException();
    }

    @Override
    public void accept(@Nonnull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor)visitor).visitModifierList(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    public String toString() {
        return "PsiModifierList";
    }

    public String[] getModifiers() {
        return ArrayUtil.toStringArray(myModifiers);
    }
}
