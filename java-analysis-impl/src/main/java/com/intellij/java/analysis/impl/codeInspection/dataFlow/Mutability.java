/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.inference.JavaSourceInference;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.impl.psi.impl.source.PsiMethodImpl;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.component.util.ModificationTracker;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nullable;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.PropertyKey;

import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public enum Mutability {
    /**
     * Mutability is not known; probably value can be mutated
     */
    UNKNOWN("mutability.unknown", null),
    /**
     * A value is known to be mutable (e.g. elements are sometimes added to the collection)
     */
    MUTABLE("mutability.modifiable", null),
    /**
     * A value is known to be an immutable view over a possibly mutable value: it cannot be mutated directly using this
     * reference; however subsequent reads (e.g. {@link Collection#size}) may return different results if the
     * underlying value is mutated by somebody else.
     */
    UNMODIFIABLE_VIEW("mutability.unmodifiable.view", "org.jetbrains.annotations.UnmodifiableView"),
    /**
     * A value is known to be immutable. For collection no elements could be added, removed or altered (though if collection
     * contains mutable elements, they still could be mutated).
     */
    UNMODIFIABLE("mutability.unmodifiable", "org.jetbrains.annotations.Unmodifiable");

    public static final
    @Nonnull
    String UNMODIFIABLE_ANNOTATION = UNMODIFIABLE.myAnnotation;
    public static final
    @Nonnull
    String UNMODIFIABLE_VIEW_ANNOTATION = UNMODIFIABLE_VIEW.myAnnotation;
    private final
    @PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE)
    String myResourceKey;
    private final String myAnnotation;
    private final Key<CachedValue<PsiAnnotation>> myKey;

    Mutability(@PropertyKey(resourceBundle = JavaAnalysisBundle.BUNDLE) String resourceKey, String annotation) {
        myResourceKey = resourceKey;
        myAnnotation = annotation;
        myKey = annotation == null ? null : Key.create(annotation);
    }

    public DfReferenceType asDfType() {
        return DfTypes.customObject(TypeConstraints.TOP, DfaNullability.UNKNOWN, this, null, DfTypes.BOTTOM);
    }

    public
    @Nonnull
    @Nls
    String getPresentationName() {
        return JavaAnalysisBundle.message(myResourceKey);
    }

    public boolean isUnmodifiable() {
        return this == UNMODIFIABLE || this == UNMODIFIABLE_VIEW;
    }

    @Nonnull
    public Mutability unite(Mutability other) {
        if (this == other) {
            return this;
        }
        if (this == UNKNOWN || other == UNKNOWN) {
            return UNKNOWN;
        }
        if (this == MUTABLE || other == MUTABLE) {
            return MUTABLE;
        }
        if (this == UNMODIFIABLE_VIEW || other == UNMODIFIABLE_VIEW) {
            return UNMODIFIABLE_VIEW;
        }
        return UNMODIFIABLE;
    }

    @Nonnull
    public Mutability intersect(Mutability other) {
        if (this == other) {
            return this;
        }
        if (this == UNMODIFIABLE || other == UNMODIFIABLE) {
            return UNMODIFIABLE;
        }
        if (this == UNMODIFIABLE_VIEW || other == UNMODIFIABLE_VIEW) {
            return UNMODIFIABLE_VIEW;
        }
        if (this == MUTABLE || other == MUTABLE) {
            return MUTABLE;
        }
        return UNKNOWN;
    }

    @Nullable
    public PsiAnnotation asAnnotation(Project project) {
        if (myAnnotation == null) {
            return null;
        }
        return CachedValuesManager.getManager(project).getCachedValue(
            project,
            myKey,
            () -> {
                PsiAnnotation annotation =
                    JavaPsiFacade.getElementFactory(project).createAnnotationFromText("@" + myAnnotation, null);
                ((LightVirtualFile)annotation.getContainingFile().getViewProvider().getVirtualFile()).setWritable(false);
                return CachedValueProvider.Result.create(annotation, ModificationTracker.NEVER_CHANGED);
            },
            false
        );
    }

    /**
     * Returns a mutability of the supplied element, if known. The element could be a method
     * (in this case the return value mutability is returned), a method parameter
     * (the returned mutability will reflect whether the method can mutate the parameter),
     * or a field (in this case the mutability could be obtained from its initializer).
     *
     * @param owner an element to check the mutability
     * @return a Mutability enum value; {@link #UNKNOWN} if cannot be determined or specified element type is not supported.
     */
    @Nonnull
    public static Mutability getMutability(@Nonnull PsiModifierListOwner owner) {
        if (owner instanceof LightElement) {
            return UNKNOWN;
        }
        return LanguageCachedValueUtil.getCachedValue(
            owner,
            () -> CachedValueProvider.Result.create(calcMutability(owner), owner, PsiModificationTracker.MODIFICATION_COUNT)
        );
    }

    @Nonnull
    private static Mutability calcMutability(@Nonnull PsiModifierListOwner owner) {
        if (owner instanceof PsiParameter && owner.getParent() instanceof PsiParameterList) {
            PsiParameterList list = (PsiParameterList)owner.getParent();
            PsiMethod method = ObjectUtil.tryCast(list.getParent(), PsiMethod.class);
            if (method != null) {
                int index = list.getParameterIndex((PsiParameter)owner);
                JavaMethodContractUtil.ContractInfo contractInfo = JavaMethodContractUtil.getContractInfo(method);
                if (contractInfo.isExplicit()) {
                    MutationSignature signature = contractInfo.getMutationSignature();
                    if (signature.mutatesArg(index)) {
                        return MUTABLE;
                    }
                    else if (signature.preservesArg(index)
                        && PsiTreeUtil.findChildOfAnyType(method.getBody(), PsiLambdaExpression.class, PsiClass.class) == null) {
                        // If method preserves argument, it still may return a lambda which captures an argument and changes it
                        // TODO: more precise check (at least differentiate parameters which are captured by lambdas or not)
                        return UNMODIFIABLE_VIEW;
                    }
                }
                return UNKNOWN;
            }
        }
        if (AnnotationUtil.isAnnotated(
            owner,
            Collections.singleton(UNMODIFIABLE_ANNOTATION),
            AnnotationUtil.CHECK_HIERARCHY | AnnotationUtil.CHECK_EXTERNAL | AnnotationUtil.CHECK_INFERRED
        )) {
            return UNMODIFIABLE;
        }
        if (AnnotationUtil.isAnnotated(
            owner,
            Collections.singleton(UNMODIFIABLE_VIEW_ANNOTATION),
            AnnotationUtil.CHECK_HIERARCHY | AnnotationUtil.CHECK_EXTERNAL | AnnotationUtil.CHECK_INFERRED
        )) {
            return UNMODIFIABLE_VIEW;
        }
        if (owner instanceof PsiField && owner.hasModifierProperty(PsiModifier.FINAL)) {
            PsiField field = (PsiField)owner;
            List<PsiExpression> initializers = ContainerUtil.createMaybeSingletonList(field.getInitializer());
            if (initializers.isEmpty() && !owner.hasModifierProperty(PsiModifier.STATIC)) {
                initializers = DfaPsiUtil.findAllConstructorInitializers(field);
            }
            initializers = StreamEx.of(initializers).flatMap(ExpressionUtils::nonStructuralChildren).toList();
            if (initializers.isEmpty()) {
                return UNKNOWN;
            }
            Mutability mutability = UNMODIFIABLE;
            for (PsiExpression initializer : initializers) {
                Mutability newMutability = UNKNOWN;
                if (ClassUtils.isImmutable(initializer.getType())) {
                    newMutability = UNMODIFIABLE;
                }
                else if (initializer instanceof PsiMethodCallExpression) {
                    PsiMethod method = ((PsiMethodCallExpression)initializer).resolveMethod();
                    newMutability = method == null ? UNKNOWN : getMutability(method);
                }
                mutability = mutability.unite(newMutability);
                if (!mutability.isUnmodifiable()) {
                    break;
                }
            }
            return mutability;
        }
        return owner instanceof PsiMethodImpl ? JavaSourceInference.inferMutability((PsiMethodImpl)owner) : UNKNOWN;
    }

    public static Mutability fromDfType(DfType dfType) {
        return dfType instanceof DfReferenceType ? ((DfReferenceType)dfType).getMutability() : UNKNOWN;
    }
}
