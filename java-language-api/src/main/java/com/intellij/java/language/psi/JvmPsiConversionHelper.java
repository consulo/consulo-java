// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.JvmMethod;
import com.intellij.java.language.jvm.JvmTypeDeclaration;
import com.intellij.java.language.jvm.JvmTypeParameter;
import com.intellij.java.language.jvm.types.JvmSubstitutor;
import com.intellij.java.language.jvm.types.JvmType;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

public interface JvmPsiConversionHelper {
    static JvmPsiConversionHelper getInstance(Project project) {
        return project.getService(JvmPsiConversionHelper.class);
    }

    @Nullable
    PsiClass convertTypeDeclaration(@Nullable JvmTypeDeclaration typeDeclaration);

    PsiTypeParameter convertTypeParameter(JvmTypeParameter typeParameter);

    PsiType convertType(JvmType type);

    PsiSubstitutor convertSubstitutor(JvmSubstitutor substitutor);

    PsiMethod convertMethod(JvmMethod method);
}
