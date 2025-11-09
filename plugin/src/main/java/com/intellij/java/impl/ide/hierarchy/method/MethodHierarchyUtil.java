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
package com.intellij.java.impl.ide.hierarchy.method;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.MethodSignatureUtil;

final class MethodHierarchyUtil {
    public static PsiMethod findBaseMethodInClass(PsiMethod baseMethod, PsiClass aClass, boolean checkBases) {
        if (baseMethod == null) {
            return null; // base method is invalid
        }
        if (cannotBeOverriding(baseMethod)) {
            return null;
        }
        /*if (!checkBases) return MethodSignatureUtil.findMethodBySignature(aClass, signature, false);*/
        return MethodSignatureUtil.findMethodBySuperMethod(aClass, baseMethod, checkBases);
        /*MethodSignatureBackedByPsiMethod signature = SuperMethodsSearch.search(baseMethod, aClass, checkBases, false).findFirst();
        return signature == null ? null : signature.getMethod();*/
    }

    private static boolean cannotBeOverriding(PsiMethod method) {
        PsiClass parentClass = method.getContainingClass();
        return parentClass == null || method.isConstructor() || method.isStatic() || method.isPrivate();
    }
}
