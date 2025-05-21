// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.annotation.JvmAnnotationArrayValue;
import com.intellij.java.language.jvm.annotation.JvmAnnotationAttributeValue;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;

import java.util.List;

class PsiAnnotationArrayValue extends PsiAnnotationAttributeValue<PsiArrayInitializerMemberValue> implements JvmAnnotationArrayValue {
    PsiAnnotationArrayValue(@Nonnull PsiArrayInitializerMemberValue value) {
        super(value);
    }

    @Nonnull
    @Override
    public List<JvmAnnotationAttributeValue> getValues() {
        return ContainerUtil.map(myElement.getInitializers(), PsiJvmConversionHelper::getAnnotationAttributeValue);
    }
}
