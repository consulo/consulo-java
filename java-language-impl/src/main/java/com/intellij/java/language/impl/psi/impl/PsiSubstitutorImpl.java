// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl;

import com.intellij.java.language.impl.psi.PsiTypeMapper;
import com.intellij.java.language.impl.psi.impl.light.LightTypeParameter;
import com.intellij.java.language.impl.psi.impl.source.PsiClassReferenceType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.RecursionManager;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.logging.Logger;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.HashingStrategy;
import consulo.util.collection.UnmodifiableHashMap;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author ik, dsl
 */
public final class PsiSubstitutorImpl implements PsiSubstitutor {
    private static final Logger LOG = Logger.getInstance(PsiSubstitutorImpl.class);

    private static final HashingStrategy<PsiTypeParameter> PSI_EQUIVALENCE = new HashingStrategy<>() {
        @Override
        @RequiredReadAction
        public int hashCode(PsiTypeParameter parameter) {
            return Comparing.hashcode(parameter.getName());
        }

        @Override
        public boolean equals(PsiTypeParameter element1, PsiTypeParameter element2) {
            return element1.getManager().areElementsEquivalent(element1, element2);
        }
    };
    private static final UnmodifiableHashMap<PsiTypeParameter, PsiType> EMPTY_MAP = UnmodifiableHashMap.empty(PSI_EQUIVALENCE);

    @Nonnull
    private final UnmodifiableHashMap<PsiTypeParameter, PsiType> mySubstitutionMap;
    private final SubstitutionVisitor mySimpleSubstitutionVisitor = new SubstitutionVisitor();

    PsiSubstitutorImpl(@Nonnull Map<? extends PsiTypeParameter, ? extends PsiType> map) {
        mySubstitutionMap = UnmodifiableHashMap.fromMap(PSI_EQUIVALENCE, map);
    }

    private PsiSubstitutorImpl(
        @Nonnull UnmodifiableHashMap<PsiTypeParameter, PsiType> map,
        @Nonnull PsiTypeParameter additionalKey,
        @Nullable PsiType additionalValue
    ) {
        mySubstitutionMap = map.with(additionalKey, additionalValue);
    }

    PsiSubstitutorImpl(@Nonnull PsiTypeParameter typeParameter, PsiType mapping) {
        mySubstitutionMap = EMPTY_MAP.with(typeParameter, mapping);
    }

    PsiSubstitutorImpl(@Nonnull PsiClass parentClass, PsiType[] mappings) {
        this(putAllInternal(EMPTY_MAP, parentClass, mappings));
    }

    @Override
    public PsiType substitute(@Nonnull PsiTypeParameter typeParameter) {
        PsiType type = getFromMap(typeParameter);
        return PsiType.VOID.equals(type) ? JavaPsiFacade.getElementFactory(typeParameter.getProject()).createType(typeParameter) : type;
    }

    /**
     * @return type mapped to type parameter; null if type parameter is mapped to null; or PsiType.VOID if no mapping exists
     */
    private PsiType getFromMap(@Nonnull PsiTypeParameter typeParameter) {
        if (typeParameter instanceof LightTypeParameter lightTypeParam && lightTypeParam.useDelegateToSubstitute()) {
            typeParameter = lightTypeParam.getDelegate();
        }
        return mySubstitutionMap.getOrDefault(typeParameter, PsiType.VOID);
    }

    @Override
    public PsiType substitute(PsiType type) {
        if (type == null) {
            return null;
        }
        PsiUtil.ensureValidType(type);
        PsiType substituted = type.accept(mySimpleSubstitutionVisitor);
        return correctExternalSubstitution(substituted, type);
    }

    @Override
    public PsiType substituteWithBoundsPromotion(@Nonnull PsiTypeParameter typeParameter) {
        PsiType substituted = substitute(typeParameter);
        if (substituted instanceof PsiWildcardType wildcardType && !wildcardType.isSuper()) {
            PsiType glb = PsiCapturedWildcardType.captureUpperBound(typeParameter, wildcardType, this);
            if (glb instanceof PsiWildcardType) {
                return glb;
            }
            if (glb instanceof PsiCapturedWildcardType capturedWildcardType) {
                PsiWildcardType wildcard = capturedWildcardType.getWildcard();
                if (!wildcard.isSuper()) {
                    return wildcard;
                }
            }

            if (glb != null) {
                return PsiWildcardType.createExtends(typeParameter.getManager(), glb);
            }
        }
        return substituted;
    }

    @Override
    public boolean equals(Object o) {
        return this == o
            || o instanceof PsiSubstitutorImpl that
            && mySubstitutionMap.equals(that.mySubstitutionMap);
    }

    @Override
    public int hashCode() {
        return mySubstitutionMap.hashCode();
    }

    private PsiType rawTypeForTypeParameter(@Nonnull PsiTypeParameter typeParameter) {
        PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
        if (extendsTypes.length > 0) {
            // First bound
            return RecursionManager.doPreventingRecursion(extendsTypes[0], true, () -> substitute(extendsTypes[0]));
        }
        // Object
        return PsiType.getJavaLangObject(typeParameter.getManager(), typeParameter.getResolveScope());
    }

    @Nonnull
    private static TypeAnnotationProvider getMergedProvider(@Nonnull PsiType type1, @Nonnull PsiType type2) {
        if (type1.getAnnotationProvider() == TypeAnnotationProvider.EMPTY && !(type1 instanceof PsiClassReferenceType)) {
            return type2.getAnnotationProvider();
        }
        if (type2.getAnnotationProvider() == TypeAnnotationProvider.EMPTY && !(type2 instanceof PsiClassReferenceType)) {
            return type1.getAnnotationProvider();
        }
        return () -> ArrayUtil.mergeArrays(type1.getAnnotations(), type2.getAnnotations());
    }

    private class SubstitutionVisitor extends PsiTypeMapper {

        @Override
        public PsiType visitType(@Nonnull PsiType type) {
            return null;
        }

        @Override
        public PsiType visitWildcardType(@Nonnull PsiWildcardType wildcardType) {
            PsiType bound = wildcardType.getBound();
            if (bound == null) {
                return wildcardType;
            }
            else {
                PsiType newBound = bound.accept(this);
                if (newBound == null) {
                    return null;
                }
                assert newBound.isValid() : newBound.getClass() + "; " + bound.isValid();
                if (newBound instanceof PsiWildcardType newBoundWildcardType) {
                    PsiType newBoundBound = newBoundWildcardType.getBound();
                    return !newBoundWildcardType.isBounded()
                        ? PsiWildcardType.createUnbounded(wildcardType.getManager())
                        : rebound(wildcardType, newBoundBound);
                }

                return newBound == PsiType.NULL ? newBound : rebound(wildcardType, newBound);
            }
        }

        @Nonnull
        private PsiWildcardType rebound(@Nonnull PsiWildcardType type, @Nonnull PsiType newBound) {
            LOG.assertTrue(type.getBound() != null);
            LOG.assertTrue(newBound.isValid());

            if (type.isExtends()) {
                if (newBound.equalsToText(JavaClassNames.JAVA_LANG_OBJECT)) {
                    return PsiWildcardType.createUnbounded(type.getManager());
                }
                return PsiWildcardType.createExtends(type.getManager(), newBound);
            }
            return PsiWildcardType.createSuper(type.getManager(), newBound);
        }

        @Override
        public PsiType visitClassType(@Nonnull PsiClassType classType) {
            PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
            PsiClass aClass = resolveResult.getElement();
            if (aClass == null) {
                return classType;
            }

            PsiUtilCore.ensureValid(aClass);
            if (aClass instanceof PsiTypeParameter typeParam) {
                PsiType result = getFromMap(typeParam);
                if (PsiType.VOID.equals(result)) {
                    return classType;
                }
                if (result != null) {
                    PsiUtil.ensureValidType(result);
                    if (result instanceof PsiClassType || result instanceof PsiArrayType || result instanceof PsiWildcardType) {
                        return result.annotate(getMergedProvider(classType, result));
                    }
                }
                return result;
            }
            Map<PsiTypeParameter, PsiType> hashMap = new HashMap<>(2);
            if (!processClass(aClass, resolveResult.getSubstitutor(), hashMap)) {
                return null;
            }
            PsiClassType result = JavaPsiFacade.getElementFactory(aClass.getProject())
                .createType(aClass, PsiSubstitutor.createSubstitutor(hashMap), classType.getLanguageLevel());
            PsiUtil.ensureValidType(result);
            return result.annotate(classType.getAnnotationProvider());
        }

        private PsiType substituteInternal(@Nonnull PsiType type) {
            return type.accept(this);
        }

        private boolean processClass(
            @Nonnull PsiClass resolve,
            @Nonnull PsiSubstitutor originalSubstitutor,
            @Nonnull Map<PsiTypeParameter, PsiType> substMap
        ) {
            PsiTypeParameter[] params = resolve.getTypeParameters();
            for (PsiTypeParameter param : params) {
                PsiType original = originalSubstitutor.substitute(param);
                if (original == null) {
                    substMap.put(param, null);
                }
                else {
                    substMap.put(param, substituteInternal(original));
                }
            }
            if (resolve.isStatic()) {
                return true;
            }

            PsiClass containingClass = resolve.getContainingClass();
            return containingClass == null ||
                processClass(containingClass, originalSubstitutor, substMap);
        }
    }

    private PsiType correctExternalSubstitution(PsiType substituted, @Nonnull PsiType original) {
        if (substituted != null) {
            return substituted;
        }
        return original.accept(new PsiTypeVisitor<PsiType>() {
            @Override
            public PsiType visitArrayType(@Nonnull PsiArrayType arrayType) {
                return new PsiArrayType(arrayType.getComponentType().accept(this));
            }

            @Override
            public PsiType visitEllipsisType(@Nonnull PsiEllipsisType ellipsisType) {
                return new PsiEllipsisType(ellipsisType.getComponentType().accept(this));
            }

            @Override
            public PsiType visitClassType(@Nonnull PsiClassType classType) {
                PsiClass aClass = classType.resolve();
                if (aClass == null) {
                    return classType;
                }
                if (aClass instanceof PsiTypeParameter) {
                    return rawTypeForTypeParameter((PsiTypeParameter)aClass);
                }
                return JavaPsiFacade.getElementFactory(aClass.getProject()).createType(aClass);
            }

            @Override
            public PsiType visitType(@Nonnull PsiType type) {
                return null;
            }
        });
    }

    @Override
    protected PsiSubstitutorImpl clone() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public PsiSubstitutor put(@Nonnull PsiTypeParameter typeParameter, PsiType mapping) {
        if (mapping != null && !mapping.isValid()) {
            LOG.error("Invalid type in substitutor: " + mapping + "; " + mapping.getClass());
        }
        return new PsiSubstitutorImpl(mySubstitutionMap, typeParameter, mapping);
    }

    @Nonnull
    private static UnmodifiableHashMap<PsiTypeParameter, PsiType> putAllInternal(
        @Nonnull UnmodifiableHashMap<PsiTypeParameter, PsiType> originalMap,
        @Nonnull PsiClass parentClass,
        PsiType[] mappings
    ) {
        PsiTypeParameter[] params = parentClass.getTypeParameters();
        if (params.length == 0) {
            return originalMap;
        }
        UnmodifiableHashMap<PsiTypeParameter, PsiType> newMap = originalMap;

        for (int i = 0; i < params.length; i++) {
            PsiTypeParameter param = params[i];
            assert param != null;
            if (mappings != null && mappings.length > i) {
                PsiType mapping = mappings[i];
                newMap = newMap.with(param, mapping);
                if (mapping != null && !mapping.isValid()) {
                    LOG.error("Invalid type in substitutor: " + mapping);
                }
            }
            else {
                newMap = newMap.with(param, null);
            }
        }
        return newMap;
    }

    @Nonnull
    @Override
    public PsiSubstitutor putAll(@Nonnull PsiClass parentClass, PsiType[] mappings) {
        return new PsiSubstitutorImpl(putAllInternal(mySubstitutionMap, parentClass, mappings));
    }

    @Nonnull
    @Override
    public PsiSubstitutor putAll(@Nonnull PsiSubstitutor another) {
        if (another instanceof EmptySubstitutor) {
            return this;
        }
        PsiSubstitutorImpl anotherImpl = (PsiSubstitutorImpl)another;
        return putAll(anotherImpl.mySubstitutionMap);
    }

    @Nonnull
    @Override
    public PsiSubstitutor putAll(@Nonnull Map<? extends PsiTypeParameter, ? extends PsiType> map) {
        if (map.isEmpty()) {
            return this;
        }
        return new PsiSubstitutorImpl(mySubstitutionMap.withAll(map));
    }

    @Override
    @RequiredReadAction
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        Set<Map.Entry<PsiTypeParameter, PsiType>> set = mySubstitutionMap.entrySet();
        for (Map.Entry<PsiTypeParameter, PsiType> entry : set) {
            PsiTypeParameter typeParameter = entry.getKey();
            buffer.append(typeParameter.getName());
            PsiElement owner = typeParameter.getOwner();
            if (owner instanceof PsiClass psiClass) {
                buffer.append(" of ");
                buffer.append(psiClass.getQualifiedName());
            }
            else if (owner instanceof PsiMethod method) {
                buffer.append(" of ");
                buffer.append(method.getName());
                buffer.append(" in ");
                PsiClass aClass = method.getContainingClass();
                buffer.append(aClass != null ? aClass.getQualifiedName() : "<no class>");
            }
            buffer.append(" -> ");
            if (entry.getValue() != null) {
                buffer.append(entry.getValue().getCanonicalText());
            }
            else {
                buffer.append("null");
            }
            buffer.append('\n');
        }
        return buffer.toString();
    }

    @Override
    public boolean isValid() {
        for (Map.Entry<PsiTypeParameter, PsiType> entry : mySubstitutionMap.entrySet()) {
            if (!entry.getKey().isValid()) {
                return false;
            }

            PsiType type = entry.getValue();
            if (type != null && !type.isValid()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void ensureValid() {
        mySubstitutionMap.values().forEach(type -> {
            if (type != null) {
                PsiUtil.ensureValidType(type);
            }
        });
    }

    @Override
    @Nonnull
    public Map<PsiTypeParameter, PsiType> getSubstitutionMap() {
        return mySubstitutionMap;
    }

    /**
     * @deprecated use {@link PsiSubstitutor#createSubstitutor(Map)}
     */
    @Deprecated
    public static PsiSubstitutor createSubstitutor(@Nullable Map<? extends PsiTypeParameter, ? extends PsiType> map) {
        return PsiSubstitutor.createSubstitutor(map);
    }
}
