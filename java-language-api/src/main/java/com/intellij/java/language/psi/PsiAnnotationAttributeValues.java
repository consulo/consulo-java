// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.JvmAnnotation;
import com.intellij.java.language.jvm.JvmClass;
import com.intellij.java.language.jvm.JvmEnumField;
import com.intellij.java.language.jvm.annotation.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

import static consulo.util.collection.ContainerUtil.map;

abstract class PsiAnnotationAttributeValue<T extends PsiAnnotationMemberValue> implements JvmAnnotationAttributeValue {
    protected final T myElement;

    protected PsiAnnotationAttributeValue(@Nonnull T value) {
        myElement = value;
    }
}

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

class PsiAnnotationArrayValue extends PsiAnnotationAttributeValue<PsiArrayInitializerMemberValue> implements JvmAnnotationArrayValue {
    PsiAnnotationArrayValue(@Nonnull PsiArrayInitializerMemberValue value) {
        super(value);
    }

    @Nonnull
    @Override
    public List<JvmAnnotationAttributeValue> getValues() {
        return map(myElement.getInitializers(), PsiJvmConversionHelper::getAnnotationAttributeValue);
    }
}
