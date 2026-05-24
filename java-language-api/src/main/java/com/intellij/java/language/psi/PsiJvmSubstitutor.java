// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.JvmTypeParameter;
import com.intellij.java.language.jvm.types.JvmSubstitutor;
import com.intellij.java.language.jvm.types.JvmType;
import consulo.project.Project;
import consulo.util.collection.SmartList;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

public class PsiJvmSubstitutor implements JvmSubstitutor {

    private final Project myProject;
    private final PsiSubstitutor mySubstitutor;

    public PsiJvmSubstitutor(Project project, PsiSubstitutor substitutor) {
        myProject = project;
        mySubstitutor = substitutor;
    }

    @Override
    public Collection<JvmTypeParameter> getTypeParameters() {
        return new SmartList<>(mySubstitutor.getSubstitutionMap().keySet());
    }

    @Override
    public @Nullable JvmType substitute(JvmTypeParameter typeParameter) {
        JvmPsiConversionHelper helper = JvmPsiConversionHelper.getInstance(myProject);
        PsiTypeParameter psiTypeParameter = helper.convertTypeParameter(typeParameter);
        return mySubstitutor.substitute(psiTypeParameter);
    }

    public PsiSubstitutor getPsiSubstitutor() {
        return mySubstitutor;
    }
}
