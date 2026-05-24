// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.TypeNullability;
import com.intellij.java.language.impl.psi.impl.light.LightClassReference;
import com.intellij.java.language.impl.psi.impl.light.LightClassTypeReference;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.augment.PsiAugmentProvider;
import com.intellij.java.language.psi.infos.PatternCandidateInfo;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.util.JavaTypeNullabilityUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import static com.intellij.java.language.impl.psi.impl.source.tree.JavaSharedImplUtil.filteringTypeAnnotationProvider;
import static consulo.util.lang.ObjectUtil.notNull;

public class PsiClassReferenceType extends PsiClassType.Stub {
    private final ClassReferencePointer myReference;
    /**
     * Annotations that precede qualifier if qualifier exists.
     */
    private final TypeAnnotationProvider myQualifierAnnotationsProvider;
    private TypeNullability myNullability = null;

    public PsiClassReferenceType(PsiJavaCodeReferenceElement reference, LanguageLevel level) {
        this(reference, level, collectAnnotations(reference));
    }

    public PsiClassReferenceType(PsiJavaCodeReferenceElement reference, LanguageLevel level, PsiAnnotation[] annotations) {
        super(level, annotations);
        myReference = ClassReferencePointer.constant(reference);
        myQualifierAnnotationsProvider = TypeAnnotationProvider.EMPTY;
    }

    public PsiClassReferenceType(PsiJavaCodeReferenceElement reference, LanguageLevel level, TypeAnnotationProvider provider) {
        this(ClassReferencePointer.constant(reference), level, provider, TypeAnnotationProvider.EMPTY);
    }

    PsiClassReferenceType(ClassReferencePointer reference,
                          LanguageLevel level,
                          TypeAnnotationProvider provider,
                          TypeAnnotationProvider qualifierAnnotationsProvider) {
        this(reference, level, provider, qualifierAnnotationsProvider, null);
    }

    private PsiClassReferenceType(ClassReferencePointer reference, LanguageLevel level, TypeAnnotationProvider provider,
                                  TypeAnnotationProvider qualifierAnnotationsProvider, @Nullable TypeNullability nullability) {
        super(level, provider);
        myReference = reference;
        myQualifierAnnotationsProvider = qualifierAnnotationsProvider;
        myNullability = nullability;
    }

    private static PsiAnnotation[] collectAnnotations(PsiJavaCodeReferenceElement reference) {
        List<PsiAnnotation> result = null;
        for (PsiElement child = reference.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof PsiAnnotation) {
                if (result == null) {
                    result = new SmartList<>();
                }
                result.add((PsiAnnotation) child);
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
            for (PsiAnnotation annotation : myQualifierAnnotationsProvider.getAnnotations()) {
                if (!annotation.isValid()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean equalsToText(String text) {
        PsiJavaCodeReferenceElement reference = getReference();
        String name = reference.getReferenceName();
        return (name == null || text.contains(name)) && Objects.equals(text, getCanonicalText());
    }

    @Override
    public GlobalSearchScope getResolveScope() {
        return getReference().getResolveScope();
    }

    @Override
    public PsiAnnotation[] getAnnotations() {
        return getAnnotations(true);
    }

    @Override
    public boolean hasAnnotations() {
        if (super.hasAnnotations()) {
            return true;
        }
        PsiJavaCodeReferenceElement reference = myReference.retrieveReference();
        if (reference != null && reference.isValid() && reference.isQualified()) {
            for (PsiElement child = reference.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof PsiAnnotation) {
                    return true;
                }
            }
        }
        return false;
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
    public TypeNullability getNullability() {
        TypeNullability nullability = myNullability;
        if (nullability == null) {
            myNullability = nullability = JavaTypeNullabilityUtil.getTypeNullability(this);
        }
        return nullability;
    }

    @Override
    public PsiClassType annotate(TypeAnnotationProvider provider) {
        PsiClassReferenceType annotated = (PsiClassReferenceType) super.annotate(provider);
        if (annotated != this) {
            annotated.myNullability = null;
        }
        return annotated;
    }

    @Override
    public PsiClassType withNullability(TypeNullability nullability) {
        if (myNullability == nullability) {
            return this;
        }
        return new PsiClassReferenceType(myReference, myLanguageLevel, getAnnotationProvider(), myQualifierAnnotationsProvider, nullability);
    }

    /**
     * Returns a copy of this PsiClassReferenceType with annotations from qualifierAnnotations parameter,
     * which target is {@link PsiAnnotation.TargetType#TYPE_USE}, added to qualifier annotations.
     */
    public PsiClassReferenceType withAddedQualifierAnnotations(PsiAnnotation[] qualifierAnnotations) {
        TypeAnnotationProvider merged = filteringTypeAnnotationProvider(qualifierAnnotations, myQualifierAnnotationsProvider);
        return new PsiClassReferenceType(myReference, myLanguageLevel, getAnnotationProvider(), merged, myNullability);
    }

    @Override
    public LanguageLevel getLanguageLevel() {
        if (myLanguageLevel != null) {
            return myLanguageLevel;
        }
        return PsiUtil.getLanguageLevel(getReference());
    }

    @Override
    public PsiClassType setLanguageLevel(LanguageLevel languageLevel) {
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

        private DelegatingClassResolveResult(JavaResolveResult delegate) {
            myDelegate = delegate;
        }

        @Override
        public PsiSubstitutor getSubstitutor() {
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
        public PsiClass getElement() {
            PsiElement element = myDelegate.getElement();
            return element instanceof PsiClass ? (PsiClass) element : null;
        }

        @Override
        public @Nullable String getInferenceError() {
            return myDelegate instanceof PatternCandidateInfo ? ((PatternCandidateInfo) myDelegate).getInferenceError() : null;
        }
    }

    @Override
    public ClassResolveResult resolveGenerics() {
        PsiJavaCodeReferenceElement reference = getReference();
        if (!reference.isValid()) {
            if (reference instanceof LightClassTypeReference) {
                PsiUtil.ensureValidType(((LightClassTypeReference) reference).getType());
            }
            throw new PsiInvalidElementAccessException(reference, myReference.toString() + "; augmenters=" + PsiAugmentProvider.EP_NAME.getExtensionList());
        }
        JavaResolveResult result = reference.advancedResolve(false);
        return result.getElement() == null ? ClassResolveResult.EMPTY : new DelegatingClassResolveResult(result);
    }

    @Override
    public PsiClassType rawType() {
        PsiJavaCodeReferenceElement reference = getReference();
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiClass) {
            PsiClass aClass = (PsiClass) resolved;
            if (!PsiUtil.typeParametersIterable(aClass).iterator().hasNext()) {
                return this;
            }
            PsiManager manager = reference.getManager();
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
            PsiSubstitutor rawSubstitutor = factory.createRawSubstitutor(aClass);
            return new PsiImmediateClassType(aClass, rawSubstitutor, getLanguageLevel(), getAnnotationProvider(), null);
        }
        String qualifiedName = reference.getQualifiedName();
        String name = reference.getReferenceName();
        if (name == null) {
            name = "";
        }
        LightClassReference lightReference = new LightClassReference(reference.getManager(), name, qualifiedName, reference.getResolveScope());
        return new PsiClassReferenceType(lightReference, null, getAnnotationProvider());
    }

    @Override
    public String getClassName() {
        return getReference().getReferenceName();
    }

    @Override
    public PsiType[] getParameters() {
        PsiJavaCodeReferenceElement reference = getReference();
        if (reference.getTypeParameterCount() == 0 &&
            reference.getParent() instanceof PsiTypeElement &&
            reference.getParent().getParent() instanceof PsiDeconstructionPattern) {
            ClassResolveResult result = resolveGenerics();
            PsiClass cls = result.getElement();
            if (cls != null && result.getInferenceError() == null) {
                return ContainerUtil.map2Array(cls.getTypeParameters(), PsiType.EMPTY_ARRAY, result.getSubstitutor().getSubstitutionMap()::get);
            }
        }
        return reference.getTypeParameters();
    }

    @Override
    public int getParameterCount() {
        PsiJavaCodeReferenceElement reference = getReference();
        int count = reference.getTypeParameterCount();
        if (count == 0 &&
            reference.getParent() instanceof PsiTypeElement &&
            reference.getParent().getParent() instanceof PsiDeconstructionPattern) {
            ClassResolveResult result = resolveGenerics();
            PsiClass cls = result.getElement();
            if (cls != null && result.getInferenceError() == null) {
                return cls.getTypeParameters().length;
            }
        }
        return count;
    }

    @Override
    public String getPresentableText(boolean annotated) {
        PsiJavaCodeReferenceElement ref = getReference();
        if (!annotated) {
            return PsiNameHelper.getPresentableText(ref);
        }
        PsiAnnotation[] annotations;
        PsiElement qualifier = ref.getQualifier();
        String qualifierInfo = "";
        if (qualifier != null) {
            PsiAnnotation[] qualifierAnnotations = myQualifierAnnotationsProvider.getAnnotations();
            if (qualifierAnnotations.length > 0 && qualifier instanceof PsiJavaCodeReferenceElement &&
                ((PsiJavaCodeReferenceElement) qualifier).resolve() instanceof PsiClass) {
                // Display qualifier if it's annotated
                qualifierInfo = PsiNameHelper.getPresentableText(qualifier.getText(), qualifierAnnotations,
                    ((PsiJavaCodeReferenceElement) qualifier).getTypeParameters()) + ".";
            }
            // like java.lang.@Anno String
            annotations = notNull(PsiTreeUtil.getChildrenOfType(ref, PsiAnnotation.class), PsiAnnotation.EMPTY_ARRAY);
        }
        else {
            annotations = getAnnotations(false);
        }

        return qualifierInfo + PsiNameHelper.getPresentableText(ref.getReferenceName(), annotations, ref.getTypeParameters());
    }

    @Override
    public String getCanonicalText(boolean annotated) {
        return getText(annotated);
    }

    @Override
    public String getInternalCanonicalText() {
        return getCanonicalText(true);
    }

    private String getText(boolean annotated) {
        PsiJavaCodeReferenceElement reference = getReference();
        if (reference instanceof PsiAnnotatedJavaCodeReferenceElement) {
            PsiAnnotatedJavaCodeReferenceElement ref = (PsiAnnotatedJavaCodeReferenceElement) reference;
            PsiAnnotation[] annotations =
                annotated
                    ? ArrayUtil.mergeArrays(getAnnotations(false), myQualifierAnnotationsProvider.getAnnotations())
                    : PsiAnnotation.EMPTY_ARRAY;
            return ref.getCanonicalText(annotated, annotations.length == 0 ? null : annotations);
        }
        return reference.getCanonicalText();
    }

    public PsiJavaCodeReferenceElement getReference() {
        return myReference.retrieveNonNullReference();
    }

    @Override
    public @Nullable PsiElement getPsiContext() {
        return myReference.retrieveReference();
    }
}