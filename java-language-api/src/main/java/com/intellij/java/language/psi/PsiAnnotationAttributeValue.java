// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.annotation.JvmAnnotationAttributeValue;
import jakarta.annotation.Nonnull;

abstract class PsiAnnotationAttributeValue<T extends PsiAnnotationMemberValue> implements JvmAnnotationAttributeValue {
    protected final T myElement;

    protected PsiAnnotationAttributeValue(@Nonnull T value) {
        myElement = value;
    }
}
