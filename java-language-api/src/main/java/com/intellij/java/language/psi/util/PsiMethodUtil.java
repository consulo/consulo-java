/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.java.language.codeInsight.runner.JavaMainMethodProvider;
import com.intellij.java.language.psi.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Predicate;

/**
 * @author mike
 */
public class PsiMethodUtil {
    public static final Predicate<PsiClass> MAIN_CLASS = psiClass -> {
        if (psiClass instanceof PsiAnonymousClass) {
            return false;
        }
        //noinspection SimplifiableIfStatement
        if (psiClass.isInterface()) {
            return false;
        }
        return psiClass.getContainingClass() == null || psiClass.isStatic();
    };

    private PsiMethodUtil() {
    }

    @Nullable
    public static PsiMethod findMainMethod(PsiClass aClass) {
        for (JavaMainMethodProvider provider : JavaMainMethodProvider.EP_NAME.getExtensionList()) {
            if (provider.isApplicable(aClass)) {
                return provider.findMainInClass(aClass);
            }
        }
        PsiMethod[] mainMethods = aClass.findMethodsByName("main", true);
        return findMainMethod(mainMethods);
    }

    @Nullable
    private static PsiMethod findMainMethod(PsiMethod[] mainMethods) {
        for (PsiMethod mainMethod : mainMethods) {
            if (isMainMethod(mainMethod)) {
                return mainMethod;
            }
        }
        return null;
    }

    public static boolean isMainMethod(PsiMethod method) {
        if (method == null || method.getContainingClass() == null) {
            return false;
        }
        if (!PsiType.VOID.equals(method.getReturnType())) {
            return false;
        }
        if (!method.isStatic()) {
            return false;
        }
        if (!method.isPublic()) {
            return false;
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != 1) {
            return false;
        }
        PsiType type = parameters[0].getType();
        if (!(type instanceof PsiArrayType arrayType)) {
            return false;
        }
        PsiType componentType = arrayType.getComponentType();
        return componentType.equalsToText(CommonClassNames.JAVA_LANG_STRING);
    }

    public static boolean hasMainMethod(PsiClass psiClass) {
        for (JavaMainMethodProvider provider : JavaMainMethodProvider.EP_NAME.getExtensionList()) {
            if (provider.isApplicable(psiClass)) {
                return provider.hasMainMethod(psiClass);
            }
        }
        return findMainMethod(psiClass.findMethodsByName("main", true)) != null;
    }

    /**
     * Determines if the given class has a main method and can be launched.
     *
     * @param aClass the class to check for a main method.
     * @return true if the class has a main method, false otherwise.
     */
    public static boolean hasMainInClass(@Nonnull PsiClass aClass) {
        return MAIN_CLASS.test(aClass) && hasMainMethod(aClass);
    }

    @Nullable
    public static PsiMethod findMainInClass(PsiClass aClass) {
        if (!MAIN_CLASS.test(aClass)) {
            return null;
        }
        for (JavaMainMethodProvider provider : JavaMainMethodProvider.EP_NAME.getExtensionList()) {
            if (provider.isApplicable(aClass)) {
                return provider.findMainInClass(aClass);
            }
        }
        return findMainMethod(aClass);
    }
}
