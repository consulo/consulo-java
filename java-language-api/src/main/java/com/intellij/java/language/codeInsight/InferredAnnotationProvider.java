// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.codeInsight;

import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiModifierListOwner;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

import java.util.List;

/**
 * Returns annotations inferred by bytecode or source code, for example contracts and nullity.
 * Don't invoke these extensions directly, use {@link InferredAnnotationsManager} instead.
 */
@ExtensionAPI(ComponentScope.PROJECT)
public interface InferredAnnotationProvider {
    ExtensionPointName<InferredAnnotationProvider> EP_NAME = ExtensionPointName.create(InferredAnnotationProvider.class);

    /**
     * @return if exists, an inferred annotation by given qualified name on a given PSI element. Several invocations may return several
     * different instances of {@link PsiAnnotation}, which are not guaranteed to be equal.
     */
    @Nullable
    PsiAnnotation findInferredAnnotation(@Nonnull PsiModifierListOwner listOwner, @Nonnull String annotationFQN);

    /**
     * When annotation name is known, prefer {@link #findInferredAnnotation(PsiModifierListOwner, String)} as
     * potentially faster.
     *
     * @return all inferred annotations for the given element.
     */
    @Nonnull
    List<PsiAnnotation> findInferredAnnotations(@Nonnull PsiModifierListOwner listOwner);

}
