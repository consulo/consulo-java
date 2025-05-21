/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.cache.ModifierFlags;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;

public class ClsModifierListImpl extends ClsRepositoryPsiElement<PsiModifierListStub> implements PsiModifierList {
    public ClsModifierListImpl(PsiModifierListStub stub) {
        super(stub);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiElement[] getChildren() {
        return getAnnotations();
    }

    @Override
    @RequiredReadAction
    public boolean hasModifierProperty(@Nonnull String name) {
        return ModifierFlags.hasModifierProperty(name, getStub().getModifiersMask());
    }

    @Override
    @RequiredReadAction
    public boolean hasExplicitModifier(@Nonnull String name) {
        return hasModifierProperty(name);
    }

    @Override
    public void setModifierProperty(@Nonnull String name, boolean value) throws IncorrectOperationException {
        throw cannotModifyException(this);
    }

    @Override
    public void checkSetModifierProperty(@Nonnull String name, boolean value) throws IncorrectOperationException {
        throw cannotModifyException(this);
    }

    @Override
    @Nonnull
    public PsiAnnotation[] getAnnotations() {
        return getStub().getChildrenByType(JavaStubElementTypes.ANNOTATION, PsiAnnotation.ARRAY_FACTORY);
    }

    @Override
    @Nonnull
    public PsiAnnotation[] getApplicableAnnotations() {
        return getAnnotations();
    }

    @Override
    public PsiAnnotation findAnnotation(@Nonnull String qualifiedName) {
        return PsiImplUtil.findAnnotation(this, qualifiedName);
    }

    @Override
    @Nonnull
    public PsiAnnotation addAnnotation(@Nonnull String qualifiedName) {
        throw cannotModifyException(this);
    }

    @Override
    @RequiredReadAction
    public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
        PsiElement parent = getParent();
        PsiAnnotation[] annotations = getAnnotations();
        boolean separateAnnotations = parent instanceof PsiClass
            || parent instanceof PsiMethod
            || parent instanceof PsiField
            || parent instanceof PsiJavaModule;

        for (PsiAnnotation annotation : annotations) {
            appendText(annotation, indentLevel, buffer, separateAnnotations ? NEXT_LINE : " ");
        }

        boolean isClass = parent instanceof PsiClass;
        boolean isInterface = isClass && ((PsiClass)parent).isInterface();
        boolean isEnum = isClass && ((PsiClass)parent).isEnum();
        boolean isInterfaceClass = isClass && parent.getParent() instanceof PsiClass outerClass && outerClass.isInterface();
        boolean isMethod = parent instanceof PsiMethod;
        boolean isInterfaceMethod = isMethod && parent.getParent() instanceof PsiClass psiClass && psiClass.isInterface();
        boolean isField = parent instanceof PsiField;
        boolean isInterfaceField = isField && parent.getParent() instanceof PsiClass psiClass && psiClass.isInterface();
        boolean isEnumConstant = parent instanceof PsiEnumConstant;

        if (hasModifierProperty(PsiModifier.PUBLIC) && !isInterfaceMethod && !isInterfaceField && !isInterfaceClass && !isEnumConstant) {
            buffer.append(PsiModifier.PUBLIC).append(' ');
        }
        if (hasModifierProperty(PsiModifier.PROTECTED)) {
            buffer.append(PsiModifier.PROTECTED).append(' ');
        }
        if (hasModifierProperty(PsiModifier.PRIVATE)) {
            buffer.append(PsiModifier.PRIVATE).append(' ');
        }
        if (hasModifierProperty(PsiModifier.STATIC) && !isInterfaceField && !isEnumConstant) {
            buffer.append(PsiModifier.STATIC).append(' ');
        }
        if (hasModifierProperty(PsiModifier.ABSTRACT) && !isInterface && !isInterfaceMethod) {
            buffer.append(PsiModifier.ABSTRACT).append(' ');
        }
        if (hasModifierProperty(PsiModifier.FINAL) && !isEnum && !isInterfaceField && !isEnumConstant) {
            buffer.append(PsiModifier.FINAL).append(' ');
        }
        if (hasModifierProperty(PsiModifier.NATIVE)) {
            buffer.append(PsiModifier.NATIVE).append(' ');
        }
        if (hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
            buffer.append(PsiModifier.SYNCHRONIZED).append(' ');
        }
        if (hasModifierProperty(PsiModifier.TRANSIENT)) {
            buffer.append(PsiModifier.TRANSIENT).append(' ');
        }
        if (hasModifierProperty(PsiModifier.VOLATILE)) {
            buffer.append(PsiModifier.VOLATILE).append(' ');
        }
        if (hasModifierProperty(PsiModifier.STRICTFP)) {
            buffer.append(PsiModifier.STRICTFP).append(' ');
        }
        if (hasModifierProperty(PsiModifier.DEFAULT)) {
            buffer.append(PsiModifier.DEFAULT).append(' ');
        }
        if (hasModifierProperty(PsiModifier.OPEN)) {
            buffer.append(PsiModifier.OPEN).append(' ');
        }
        if (hasModifierProperty(PsiModifier.TRANSITIVE)) {
            buffer.append(PsiModifier.TRANSITIVE).append(' ');
        }
    }

    @Override
    public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
        setMirrorCheckingType(element, JavaElementType.MODIFIER_LIST);
        setMirrors(getAnnotations(), SourceTreeToPsiMap.<PsiModifierList>treeToPsiNotNull(element).getAnnotations());
    }

    @Override
    public void accept(@Nonnull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor elemVisitor) {
            elemVisitor.visitModifierList(this);
        }
        else {
            visitor.visitElement(this);
        }
    }

    @Override
    public String toString() {
        return "PsiModifierList";
    }
}