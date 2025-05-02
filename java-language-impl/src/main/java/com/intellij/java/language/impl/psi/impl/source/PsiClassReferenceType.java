// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.impl.psi.impl.light.LightClassReference;
import com.intellij.java.language.impl.psi.impl.light.LightClassTypeReference;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.augment.PsiAugmentProvider;
import com.intellij.java.language.psi.infos.PatternCandidateInfo;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public class PsiClassReferenceType extends PsiClassType.Stub {
    private final ClassReferencePointer myReference;

    public PsiClassReferenceType(@Nonnull PsiJavaCodeReferenceElement reference, LanguageLevel level) {
        this(reference, level, collectAnnotations(reference));
    }

    public PsiClassReferenceType(
        @Nonnull PsiJavaCodeReferenceElement reference,
        LanguageLevel level,
        @Nonnull PsiAnnotation[] annotations
    ) {
        super(level, annotations);
        myReference = ClassReferencePointer.constant(reference);
    }

    public PsiClassReferenceType(
        @Nonnull PsiJavaCodeReferenceElement reference,
        LanguageLevel level,
        @Nonnull TypeAnnotationProvider provider
    ) {
        this(ClassReferencePointer.constant(reference), level, provider);
    }

    PsiClassReferenceType(@Nonnull ClassReferencePointer reference, LanguageLevel level, @Nonnull TypeAnnotationProvider provider) {
        super(level, provider);
        myReference = reference;
    }

    @Nonnull
    private static PsiAnnotation[] collectAnnotations(PsiJavaCodeReferenceElement reference) {
        List<PsiAnnotation> result = null;
        for (PsiElement child = reference.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiAnnotation) {
                if (result == null) {
                    result = new SmartList<>();
                }
                result.add((PsiAnnotation)child);
            }
        }
        return result == null ? PsiAnnotation.EMPTY_ARRAY : result.toArray(PsiAnnotation.EMPTY_ARRAY);
    }

    @Override
    public boolean isValid() {
        PsiJavaCodeReferenceElement reference = myReference.retrieveReference();
        if (reference != null && reference.isValid()) {
            for (PsiAnnotation annotation : getAnnotations(false)) {
                if (!annotation.isValid()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean equalsToText(@Nonnull String text) {
        PsiJavaCodeReferenceElement reference = getReference();
        String name = reference.getReferenceName();
        return (name == null || text.contains(name)) && Objects.equals(text, getCanonicalText());
    }

    @Override
    @Nonnull
    public GlobalSearchScope getResolveScope() {
        return getReference().getResolveScope();
    }

    @Override
    @Nonnull
    public PsiAnnotation[] getAnnotations() {
        return getAnnotations(true);
    }

    private PsiAnnotation[] getAnnotations(boolean merge) {
        PsiAnnotation[] annotations = super.getAnnotations();

        if (merge) {
            PsiJavaCodeReferenceElement reference = myReference.retrieveReference();
            if (reference != null && reference.isValid() && reference.isQualified()) {
                PsiAnnotation[] embedded = collectAnnotations(reference);
                if (annotations.length > 0 && embedded.length > 0) {
                    LinkedHashSet<PsiAnnotation> set = new LinkedHashSet<>();
                    ContainerUtil.addAll(set, annotations);
                    ContainerUtil.addAll(set, embedded);
                    annotations = set.toArray(PsiAnnotation.EMPTY_ARRAY);
                }
                else {
                    annotations = ArrayUtil.mergeArrays(annotations, embedded);
                }
            }
        }

        return annotations;
    }

    @Override
    public
    @Nonnull
    LanguageLevel getLanguageLevel() {
        if (myLanguageLevel != null) {
            return myLanguageLevel;
        }
        return PsiUtil.getLanguageLevel(getReference());
    }

    @Override
    public
    @Nonnull
    PsiClassType setLanguageLevel(final @Nonnull LanguageLevel languageLevel) {
        if (languageLevel.equals(myLanguageLevel)) {
            return this;
        }
        return new PsiClassReferenceType(getReference(), languageLevel, getAnnotationProvider());
    }

    @Override
    public PsiClass resolve() {
        return resolveGenerics().getElement();
    }

    private static final class DelegatingClassResolveResult implements PsiClassType.ClassResolveResult {
        private final JavaResolveResult myDelegate;

        private DelegatingClassResolveResult(@Nonnull JavaResolveResult delegate) {
            myDelegate = delegate;
        }

        @Override
        public
        @Nonnull
        PsiSubstitutor getSubstitutor() {
            return myDelegate.getSubstitutor();
        }

        @Override
        public boolean isValidResult() {
            return myDelegate.isValidResult();
        }

        @Override
        public boolean isAccessible() {
            return myDelegate.isAccessible();
        }

        @Override
        public boolean isStaticsScopeCorrect() {
            return myDelegate.isStaticsScopeCorrect();
        }

        @Override
        public PsiElement getCurrentFileResolveScope() {
            return myDelegate.getCurrentFileResolveScope();
        }

        @Override
        public boolean isPackagePrefixPackageReference() {
            return myDelegate.isPackagePrefixPackageReference();
        }

        @Override
        @Nullable
        public String getInferenceError() {
            return myDelegate instanceof PatternCandidateInfo ? ((PatternCandidateInfo)myDelegate).getInferenceError() : null;
        }

        @Override
        public PsiClass getElement() {
            final PsiElement element = myDelegate.getElement();
            return element instanceof PsiClass ? (PsiClass)element : null;
        }
    }

    @Override
    @Nonnull
    public ClassResolveResult resolveGenerics() {
        PsiJavaCodeReferenceElement reference = getReference();
        if (!reference.isValid()) {
            if (reference instanceof LightClassTypeReference) {
                PsiUtil.ensureValidType(((LightClassTypeReference)reference).getType());
            }
            throw new PsiInvalidElementAccessException(
                reference,
                myReference.toString() + "; augmenters=" + PsiAugmentProvider.EP_NAME.getExtensionList()
            );
        }
        final JavaResolveResult result = reference.advancedResolve(false);
        return result.getElement() == null ? ClassResolveResult.EMPTY : new DelegatingClassResolveResult(result);
    }

    @Override
    @Nonnull
    public PsiClassType rawType() {
        PsiJavaCodeReferenceElement reference = getReference();
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiClass) {
            PsiClass aClass = (PsiClass)resolved;
            if (!PsiUtil.typeParametersIterable(aClass).iterator().hasNext()) {
                return this;
            }
            PsiManager manager = reference.getManager();
            final PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
            final PsiSubstitutor rawSubstitutor = factory.createRawSubstitutor(aClass);
            return new PsiImmediateClassType(aClass, rawSubstitutor, getLanguageLevel(), getAnnotationProvider());
        }
        String qualifiedName = reference.getQualifiedName();
        String name = reference.getReferenceName();
        if (name == null) {
            name = "";
        }
        LightClassReference lightReference =
            new LightClassReference(reference.getManager(), name, qualifiedName, reference.getResolveScope());
        return new PsiClassReferenceType(lightReference, null, getAnnotationProvider());
    }

    @Override
    public String getClassName() {
        return getReference().getReferenceName();
    }

    @Override
    @Nonnull
    public PsiType[] getParameters() {
        return getReference().getTypeParameters();
    }

    @Override
    public
    @Nonnull
    String getPresentableText(boolean annotated) {
        PsiJavaCodeReferenceElement ref = getReference();
        if (!annotated) {
            return PsiNameHelper.getPresentableText(ref);
        }
        PsiAnnotation[] annotations;
        if (ref.getQualifier() != null) {
            // like java.lang.@Anno String
            annotations = ObjectUtil.notNull(PsiTreeUtil.getChildrenOfType(ref, PsiAnnotation.class), PsiAnnotation.EMPTY_ARRAY);
        }
        else {
            annotations = getAnnotations(false);
        }

        return PsiNameHelper.getPresentableText(ref.getReferenceName(), annotations, ref.getTypeParameters());
    }

    @Override
    public
    @Nonnull
    String getCanonicalText(boolean annotated) {
        return getText(annotated);
    }

    @Override
    public
    @Nonnull
    String getInternalCanonicalText() {
        return getCanonicalText(true);
    }

    private String getText(boolean annotated) {
        PsiJavaCodeReferenceElement reference = getReference();
        if (reference instanceof PsiAnnotatedJavaCodeReferenceElement) {
            PsiAnnotatedJavaCodeReferenceElement ref = (PsiAnnotatedJavaCodeReferenceElement)reference;
            PsiAnnotation[] annotations = annotated ? getAnnotations(false) : PsiAnnotation.EMPTY_ARRAY;
            return ref.getCanonicalText(annotated, annotations.length == 0 ? null : annotations);
        }
        return reference.getCanonicalText();
    }

    @Nonnull
    public PsiJavaCodeReferenceElement getReference() {
        return myReference.retrieveNonNullReference();
    }

    @Override
    public
    @Nullable
    PsiElement getPsiContext() {
        return myReference.retrieveReference();
    }
}