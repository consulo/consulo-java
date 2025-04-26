/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.language.psi.util;

import com.intellij.java.language.psi.*;
import consulo.application.util.function.Processor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.util.lang.function.Condition;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

public class InheritanceUtil {
    private InheritanceUtil() {
    }

    /**
     * @param aClass    a class to check.
     * @param baseClass supposed base class.
     * @param checkDeep true to check deeper than aClass.super (see {@linkplain PsiClass#isInheritor(PsiClass, boolean)}).
     * @return true if aClass is the baseClass or baseClass inheritor
     */
    public static boolean isInheritorOrSelf(@Nullable PsiClass aClass, @Nullable PsiClass baseClass, boolean checkDeep) {
        if (aClass == null || baseClass == null) {
            return false;
        }
        PsiManager manager = aClass.getManager();
        return manager.areElementsEquivalent(baseClass, aClass) || aClass.isInheritor(baseClass, checkDeep);
    }

    public static boolean processSupers(@Nullable PsiClass aClass, boolean includeSelf, @Nonnull Predicate<PsiClass> superProcessor) {
        if (aClass == null) {
            return true;
        }

        if (includeSelf && !superProcessor.test(aClass)) {
            return false;
        }

        return processSupers(aClass, superProcessor, new HashSet<>());
    }

    private static boolean processSupers(@Nonnull PsiClass aClass, @Nonnull Predicate<PsiClass> superProcessor, @Nonnull Set<PsiClass> visited) {
        if (!visited.add(aClass)) {
            return true;
        }

        for (final PsiClass intf : aClass.getInterfaces()) {
            if (!superProcessor.test(intf) || !processSupers(intf, superProcessor, visited)) {
                return false;
            }
        }
        final PsiClass superClass = aClass.getSuperClass();
        if (superClass != null) {
            if (!superProcessor.test(superClass) || !processSupers(superClass, superProcessor, visited)) {
                return false;
            }
        }
        return true;
    }

    @Contract("null, _ -> false")
    public static boolean isInheritor(@Nullable PsiType type, @Nonnull @NonNls final String baseClassName) {
        if (type instanceof PsiClassType) {
            return isInheritor(((PsiClassType) type).resolve(), baseClassName);
        }

        if (type instanceof PsiIntersectionType) {
            for (PsiType conjunct : ((PsiIntersectionType) type).getConjuncts()) {
                if (isInheritor(conjunct, baseClassName)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Contract("null, _ -> false")
    public static boolean isInheritor(@Nullable PsiClass psiClass, @Nonnull final String baseClassName) {
        return isInheritor(psiClass, false, baseClassName);
    }

    @Contract("null, _, _ -> false")
    public static boolean isInheritor(@Nullable PsiClass psiClass, final boolean strict, @Nonnull final String baseClassName) {
        if (psiClass == null) {
            return false;
        }

        final PsiClass base = JavaPsiFacade.getInstance(psiClass.getProject()).findClass(baseClassName, psiClass.getResolveScope());
        if (base == null) {
            return false;
        }

        return strict ? psiClass.isInheritor(base, true) : isInheritorOrSelf(psiClass, base, true);
    }

    /**
     * Gets all superclasses. Classes are added to result in DFS order
     *
     * @param aClass
     * @param results
     * @param includeNonProject
     */
    public static void getSuperClasses(@Nonnull PsiClass aClass, @Nonnull Set<PsiClass> results, boolean includeNonProject) {
        getSuperClassesOfList(aClass.getSuperTypes(), results, includeNonProject, new HashSet<>(), aClass.getManager());
    }

    public static LinkedHashSet<PsiClass> getSuperClasses(@Nonnull PsiClass aClass) {
        LinkedHashSet<PsiClass> result = new LinkedHashSet<>();
        getSuperClasses(aClass, result, true);
        return result;
    }


    private static void getSuperClassesOfList(@Nonnull PsiClassType[] types, @Nonnull Set<PsiClass> results, boolean includeNonProject, @Nonnull Set<PsiClass> visited, @Nonnull PsiManager manager) {
        for (PsiClassType type : types) {
            PsiClass resolved = type.resolve();
            if (resolved != null && visited.add(resolved)) {
                if (includeNonProject || manager.isInProject(resolved)) {
                    results.add(resolved);
                }
                getSuperClassesOfList(resolved.getSuperTypes(), results, includeNonProject, visited, manager);
            }
        }
    }

    public static boolean hasEnclosingInstanceInScope(PsiClass aClass, PsiElement scope, boolean isSuperClassAccepted, boolean isTypeParamsAccepted) {
        return hasEnclosingInstanceInScope(aClass, scope, psiClass -> isSuperClassAccepted, isTypeParamsAccepted);
    }

    public static boolean hasEnclosingInstanceInScope(PsiClass aClass, PsiElement scope, Condition<PsiClass> isSuperClassAccepted, boolean isTypeParamsAccepted) {
        PsiManager manager = aClass.getManager();
        PsiElement place = scope;
        while (place != null && place != aClass && !(place instanceof PsiFile)) {
            if (place instanceof PsiClass) {
                if (isSuperClassAccepted.value((PsiClass) place)) {
                    if (isInheritorOrSelf((PsiClass) place, aClass, true)) {
                        return true;
                    }
                }
                else {
                    if (manager.areElementsEquivalent(place, aClass)) {
                        return true;
                    }
                }
                if (isTypeParamsAccepted && place instanceof PsiTypeParameter) {
                    return true;
                }
            }
            if (place instanceof PsiModifierListOwner) {
                final PsiModifierList modifierList = ((PsiModifierListOwner) place).getModifierList();
                if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
                    return false;
                }
            }
            place = place.getParent();
        }
        return place == aClass;
    }

    public static boolean processSuperTypes(@Nonnull PsiType type, boolean includeSelf, @Nonnull Processor<PsiType> processor) {
        if (includeSelf && !processor.process(type)) {
            return false;
        }
        return processSuperTypes(type, processor, new HashSet<>());
    }

    private static boolean processSuperTypes(PsiType type, Processor<PsiType> processor, Set<PsiType> visited) {
        if (!visited.add(type)) {
            return true;
        }
        for (PsiType superType : type.getSuperTypes()) {
            if (!processor.process(superType)) {
                return false;
            }
            processSuperTypes(superType, processor, visited);
        }
        return true;
    }

}