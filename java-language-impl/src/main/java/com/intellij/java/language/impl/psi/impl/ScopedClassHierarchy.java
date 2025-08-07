/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.language.impl.psi.impl;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.ConcurrentFactoryMap;
import consulo.application.util.RecursionGuard;
import consulo.application.util.RecursionManager;
import consulo.application.util.function.Computable;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.PsiSearchScopeUtil;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.HashingStrategy;
import consulo.util.collection.Maps;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.BiPredicate;

import static consulo.util.lang.ObjectUtil.assertNotNull;

/**
 * @author peter
 */
class ScopedClassHierarchy {
    private static final HashingStrategy<PsiClass> CLASS_HASHING_STRATEGY = new HashingStrategy<PsiClass>() {
        @Override
        public int hashCode(PsiClass object) {
            return StringUtil.notNullize(object.getQualifiedName()).hashCode();
        }

        @Override
        public boolean equals(PsiClass o1, PsiClass o2) {
            final String qname1 = o1.getQualifiedName();
            if (qname1 != null) {
                return qname1.equals(o2.getQualifiedName());
            }

            return o1.getManager().areElementsEquivalent(o1, o2);
        }
    };
    private static final RecursionGuard<Object> ourGuard = RecursionManager.createGuard("ScopedClassHierarchy");
    private final PsiClass myPlaceClass;
    private final GlobalSearchScope myResolveScope;
    private volatile Map<PsiClass, PsiClassType.ClassResolveResult> mySupersWithSubstitutors;
    private volatile List<PsiClassType.ClassResolveResult> myImmediateSupersWithCapturing;
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final Map<LanguageLevel, Map<PsiClass, PsiSubstitutor>> myAllSupersWithCapturing = ConcurrentFactoryMap.createMap(languageLevel -> calcAllMemberSupers(languageLevel));

    private ScopedClassHierarchy(PsiClass psiClass, GlobalSearchScope resolveScope) {
        myPlaceClass = psiClass;
        myResolveScope = resolveScope;
    }

    private void visitType(@Nonnull PsiClassType type, Map<PsiClass, PsiClassType.ClassResolveResult> map) {
        PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
        PsiClass psiClass = resolveResult.getElement();
        if (psiClass == null || InheritanceImplUtil.hasObjectQualifiedName(psiClass) || map.containsKey(psiClass)) {
            return;
        }

        map.put(psiClass, resolveResult);

        for (PsiType superType : getSuperTypes(psiClass)) {
            superType = type.isRaw() && superType instanceof PsiClassType ? ((PsiClassType) superType).rawType() : resolveResult.getSubstitutor().substitute(superType);
            superType = PsiClassImplUtil.correctType(superType, myResolveScope);
            if (superType instanceof PsiClassType) {
                visitType((PsiClassType) superType, map);
            }
        }
    }

    @Nonnull
    private static List<PsiType> getSuperTypes(PsiClass psiClass) {
        List<PsiType> superTypes = ContainerUtil.newArrayList();
        if (psiClass instanceof PsiAnonymousClass) {
            ContainerUtil.addIfNotNull(superTypes, ((PsiAnonymousClass) psiClass).getBaseClassType());
        }
        Collections.addAll(superTypes, psiClass.getExtendsListTypes());
        Collections.addAll(superTypes, psiClass.getImplementsListTypes());
        return superTypes;
    }

    @Nonnull
    static ScopedClassHierarchy getHierarchy(@Nonnull final PsiClass psiClass, @Nonnull final GlobalSearchScope resolveScope) {
        return LanguageCachedValueUtil.getCachedValue(psiClass, new CachedValueProvider<Map<GlobalSearchScope, ScopedClassHierarchy>>() {
            @Nullable
            @Override
            public Result<Map<GlobalSearchScope, ScopedClassHierarchy>> compute() {
                Map<GlobalSearchScope, ScopedClassHierarchy> result = ConcurrentFactoryMap.createMap(it -> new ScopedClassHierarchy(psiClass, it));
                return Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
            }
        }).get(resolveScope);
    }

    @Nullable
    static PsiSubstitutor getSuperClassSubstitutor(@Nonnull PsiClass derivedClass, @Nonnull GlobalSearchScope scope, @Nonnull PsiClass superClass) {
        ScopedClassHierarchy hierarchy = getHierarchy(derivedClass, scope);
        Map<PsiClass, PsiClassType.ClassResolveResult> map = hierarchy.mySupersWithSubstitutors;
        if (map == null) {
            map = Maps.newHashMap(CLASS_HASHING_STRATEGY);
            RecursionGuard.StackStamp stamp = ourGuard.markStack();
            hierarchy.visitType(JavaPsiFacade.getElementFactory(derivedClass.getProject()).createType(derivedClass, PsiSubstitutor.EMPTY), map);
            if (stamp.mayCacheNow()) {
                hierarchy.mySupersWithSubstitutors = map;
            }
        }
        PsiClassType.ClassResolveResult resolveResult = map.get(superClass);
        if (resolveResult == null) {
            return null;
        }

        PsiClass cachedClass = assertNotNull(resolveResult.getElement());
        PsiSubstitutor cachedSubstitutor = resolveResult.getSubstitutor();
        return cachedClass == superClass ? cachedSubstitutor : mirrorSubstitutor(superClass, cachedClass, cachedSubstitutor);
    }

    @Nonnull
    private static PsiSubstitutor mirrorSubstitutor(@Nonnull PsiClass from, @Nonnull final PsiClass to, @Nonnull PsiSubstitutor substitutor) {
        Iterator<PsiTypeParameter> baseParams = PsiUtil.typeParametersIterator(to);
        Iterator<PsiTypeParameter> candidateParams = PsiUtil.typeParametersIterator(from);

        PsiSubstitutor answer = PsiSubstitutor.EMPTY;
        while (baseParams.hasNext()) {
            // if equivalent classes "from" and "to" have different number of type parameters, then treat "to" as a raw type
            if (!candidateParams.hasNext()) {
                return JavaClassSupersImpl.createRawSubstitutor(to);
            }

            answer = answer.put(baseParams.next(), substitutor.substitute(candidateParams.next()));
        }
        return answer;
    }

    @Nonnull
    List<PsiClassType.ClassResolveResult> getImmediateSupersWithCapturing() {
        List<PsiClassType.ClassResolveResult> list = myImmediateSupersWithCapturing;
        if (list == null) {
            RecursionGuard.StackStamp stamp = ourGuard.markStack();
            list = ourGuard.doPreventingRecursion(this, true, new Computable<List<PsiClassType.ClassResolveResult>>() {
                @Override
                public List<PsiClassType.ClassResolveResult> compute() {
                    return calcImmediateSupersWithCapturing();
                }
            });
            if (list == null) {
                return Collections.emptyList();
            }
            if (stamp.mayCacheNow()) {
                myImmediateSupersWithCapturing = list;
            }
        }
        return list;
    }

    @Nonnull
    private List<PsiClassType.ClassResolveResult> calcImmediateSupersWithCapturing() {
        List<PsiClassType.ClassResolveResult> list;
        list = ContainerUtil.newArrayList();
        for (PsiClassType type : myPlaceClass.getSuperTypes()) {
            PsiClassType corrected = PsiClassImplUtil.correctType(type, myResolveScope);
            if (corrected == null) {
                continue;
            }

            PsiClassType.ClassResolveResult result = ((PsiClassType) PsiUtil.captureToplevelWildcards(corrected, myPlaceClass)).resolveGenerics();
            PsiClass superClass = result.getElement();
            if (superClass == null || !PsiSearchScopeUtil.isInScope(myResolveScope, superClass)) {
                continue;
            }

            list.add(result);
        }
        return list;
    }

    @Nonnull
    private Map<PsiClass, PsiSubstitutor> calcAllMemberSupers(final LanguageLevel level) {
        final Map<PsiClass, PsiSubstitutor> map = new HashMap<>();
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myPlaceClass.getProject());
        new BiPredicate<PsiClass, PsiSubstitutor>() {
            @Override
            public boolean test(PsiClass eachClass, PsiSubstitutor eachSubstitutor) {
                if (!map.containsKey(eachClass)) {
                    map.put(eachClass, eachSubstitutor);
                    PsiClassImplUtil.processSuperTypes(eachClass, eachSubstitutor, factory, level, myResolveScope, this);
                }
                return true;
            }
        }.test(myPlaceClass, PsiSubstitutor.EMPTY);
        return map;
    }

    @Nullable
    PsiSubstitutor getSuperMembersSubstitutor(@Nonnull PsiClass superClass, @Nonnull LanguageLevel level) {
        return myAllSupersWithCapturing.get(level).get(superClass);
    }
}
