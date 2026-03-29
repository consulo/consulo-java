// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.nullable;

import com.intellij.java.language.codeInsight.*;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.JavaTypeNullabilityUtil;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ObjectUtil;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;


/// Encapsulates information related to a nullability annotation.
///
@NullMarked
public final class NullabilityAnnotationWrapper {
    private final NullabilityAnnotationInfo info;

    private NullabilityAnnotationWrapper(NullabilityAnnotationInfo info) {
        this.info = info;
    }

    /// Constructs a NullabilityAnnotationWrapper for a given PsiAnnotation
    ///
    /// @return a NullabilityAnnotationWrapper instance containing nullability and annotation details,
    ///         or null if the annotation's nullability cannot be determined
    public static @Nullable NullabilityAnnotationWrapper from(PsiAnnotation annotation) {
        TypeNullability typeNullability = JavaTypeNullabilityUtil.getNullabilityFromAnnotations(new PsiAnnotation[]{annotation});
        NullabilityAnnotationInfo info = typeNullability.toNullabilityAnnotationInfo();
        if (info == null) return null;
        return new NullabilityAnnotationWrapper(info);
    }

    /// Evaluates whether the wrapped annotation is redundant within the scope of a container annotation
    /// and returns that container nullability annotation info. Otherwise, returns null.
    public @Nullable NullabilityAnnotationInfo findContainerInfoForRedundantAnnotation() {
        PsiModifierListOwner listOwner = listOwner();
        if (listOwner != null && targetType() != null) {
            NullabilityAnnotationInfo info = manager().findContainerAnnotation(listOwner);
            return isRedundantInContainerScope(info) ? info : null;
        }
        else {
            PsiType type = type();
            if (type != null) {
                PsiElement context = type instanceof PsiClassType classType ? classType.getPsiContext() : info.getAnnotation();
                if (context != null) {
                    NullabilityAnnotationInfo info = manager().findDefaultTypeUseNullability(context);
                    return isRedundantInContainerScope(info) ? info : null;
                }
            }
        }
        return null;
    }

    private boolean isRedundantInContainerScope(@Nullable NullabilityAnnotationInfo containerInfo) {
        return containerInfo != null &&
            !containerInfo.getAnnotation().equals(info.getAnnotation()) &&
            containerInfo.getNullability() == info.getNullability();
    }

    /// @return modifier list owner
    public @Nullable PsiModifierListOwner listOwner() {
        return annotation().getOwner() instanceof PsiModifierList modifierList
            ? ObjectUtil.tryCast(modifierList.getParent(), PsiModifierListOwner.class)
            : null;
    }

    /// @return related type of wrapped annotation
    public @Nullable PsiType type() {
        return AnnotationUtil.getRelatedType(annotation());
    }

    /// @return target type of wrapped annotation
    public @Nullable PsiType targetType() {
        PsiModifierListOwner listOwner = listOwner();
        return listOwner == null ? null : PsiUtil.getTypeByPsiElement(listOwner);
    }

    /// @return qualified name of wrapped annotation
    public @Nullable String qualifiedName() {
        return annotation().getQualifiedName();
    }

    /// @return wrapped annotation owner
    public @Nullable PsiAnnotationOwner owner() {
        return annotation().getOwner();
    }

    /// @return nullability represented by wrapped annotation
    public Nullability nullability() {
        return info.getNullability();
    }

    /// @return wrapped annotation
    public PsiAnnotation annotation() {
        return info.getAnnotation();
    }

    private NullableNotNullManager manager() {
        return NullableNotNullManager.getInstance(info.getAnnotation().getProject());
    }
}
