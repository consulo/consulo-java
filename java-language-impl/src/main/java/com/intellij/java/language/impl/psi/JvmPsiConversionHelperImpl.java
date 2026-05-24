// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi;

import com.intellij.java.language.jvm.JvmMethod;
import com.intellij.java.language.jvm.JvmTypeDeclaration;
import com.intellij.java.language.jvm.JvmTypeParameter;
import com.intellij.java.language.jvm.types.JvmSubstitutor;
import com.intellij.java.language.jvm.types.JvmType;
import com.intellij.java.language.psi.*;
import org.jspecify.annotations.Nullable;

public final class JvmPsiConversionHelperImpl implements JvmPsiConversionHelper {

    @Override
    public PsiClass convertTypeDeclaration(@Nullable JvmTypeDeclaration typeDeclaration) {
        if (typeDeclaration instanceof PsiClass) return (PsiClass) typeDeclaration;
        throw new RuntimeException("TODO");
    }

    @Override
    public PsiTypeParameter convertTypeParameter(JvmTypeParameter typeParameter) {
        if (typeParameter instanceof PsiTypeParameter) return (PsiTypeParameter) typeParameter;
        throw new RuntimeException("TODO");
    }

    @Override
    public PsiType convertType(JvmType type) {
        if (type instanceof PsiType) return (PsiType) type;
        throw new RuntimeException("TODO");
    }

    @Override
    public PsiSubstitutor convertSubstitutor(JvmSubstitutor substitutor) {
        if (substitutor instanceof PsiJvmSubstitutor) return ((PsiJvmSubstitutor) substitutor).getPsiSubstitutor();
        PsiSubstitutor result = PsiSubstitutor.EMPTY;
        for (JvmTypeParameter parameter : substitutor.getTypeParameters()) {
            final PsiTypeParameter psiTypeParameter = convertTypeParameter(parameter);
            final JvmType substitution = substitutor.substitute(parameter);
            final PsiType psiType = substitution == null ? null : convertType(substitution);
            result = result.put(psiTypeParameter, psiType);
        }
        return result;
    }

    @Override
    public PsiMethod convertMethod(JvmMethod method) {
        if (method instanceof PsiMethod) return (PsiMethod) method;
        throw new RuntimeException("TODO");
    }
}
