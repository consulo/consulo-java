// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.annotation.JvmAnnotationConstantValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

class PsiAnnotationConstantValue extends PsiAnnotationAttributeValue<PsiExpression> implements JvmAnnotationConstantValue {
    PsiAnnotationConstantValue(@Nonnull PsiExpression value) {
        super(value);
    }

    @Nullable
    @Override
    public Object getConstantValue() {
        PsiConstantEvaluationHelper evaluationHelper = JavaPsiFacade.getInstance(myElement.getProject()).getConstantEvaluationHelper();
        return evaluationHelper.computeConstantExpression(myElement);
    }
}
