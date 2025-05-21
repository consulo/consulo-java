// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.JvmAnnotation;
import com.intellij.java.language.jvm.annotation.JvmNestedAnnotationValue;
import jakarta.annotation.Nonnull;

class PsiNestedAnnotationValue extends PsiAnnotationAttributeValue<PsiAnnotation> implements JvmNestedAnnotationValue {
    PsiNestedAnnotationValue(@Nonnull PsiAnnotation value) {
        super(value);
    }

    @Nonnull
    @Override
    public JvmAnnotation getValue() {
        return myElement;
    }
}
