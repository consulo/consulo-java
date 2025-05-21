// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.JvmClass;
import com.intellij.java.language.jvm.annotation.JvmAnnotationClassValue;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

class PsiAnnotationClassValue extends PsiAnnotationAttributeValue<PsiClassObjectAccessExpression> implements JvmAnnotationClassValue {
    PsiAnnotationClassValue(@Nonnull PsiClassObjectAccessExpression value) {
        super(value);
    }

    private PsiJavaCodeReferenceElement getReferenceElement() {
        return myElement.getOperand().getInnermostComponentReferenceElement();
    }

    @Nullable
    @Override
    public String getQualifiedName() {
        PsiJavaCodeReferenceElement referenceElement = getReferenceElement();
        return referenceElement == null ? null : referenceElement.getQualifiedName();
    }

    @Nullable
    @Override
    @RequiredReadAction
    public JvmClass getClazz() {
        PsiJavaCodeReferenceElement referenceElement = getReferenceElement();
        if (referenceElement == null) {
            return null;
        }
        PsiElement resolved = referenceElement.resolve();
        return resolved instanceof JvmClass jvmClass ? jvmClass : null;
    }
}
