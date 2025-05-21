// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.JvmEnumField;
import com.intellij.java.language.jvm.annotation.JvmAnnotationEnumFieldValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

class PsiAnnotationEnumFieldValue extends PsiAnnotationAttributeValue<PsiReferenceExpression> implements JvmAnnotationEnumFieldValue {
    private final JvmEnumField myEnumField;

    PsiAnnotationEnumFieldValue(@Nonnull PsiReferenceExpression value, @Nonnull JvmEnumField field) {
        super(value);
        myEnumField = field;
    }

    @Nullable
    @Override
    public JvmEnumField getField() {
        return myEnumField;
    }
}
