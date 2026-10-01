/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package consulo.java.diagram.impl;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public final class JavaDiagramUtil {
    private JavaDiagramUtil() {
    }

    public static @Nullable String getFQN(PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            return psiClass.getQualifiedName();
        }
        if (element instanceof PsiJavaPackage psiPackage) {
            return psiPackage.getQualifiedName();
        }
        return null;
    }

    public static @Nullable String getPackageName(@Nullable PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            PsiClass topClass = psiClass;
            if (PsiUtil.isInnerClass(psiClass)) {
                topClass = psiClass.getContainingClass();
                if (topClass == null) {
                    return null;
                }
            }
            String fqn = topClass.getQualifiedName();
            if (fqn == null) {
                return null;
            }
            int index = fqn.lastIndexOf('.');
            return index > 0 ? fqn.substring(0, index) : fqn;
        }
        if (element instanceof PsiJavaPackage psiPackage) {
            return psiPackage.getQualifiedName();
        }
        return null;
    }

    public static @Nullable String getRealPackageName(@Nullable PsiElement psiElement) {
        if (psiElement instanceof PsiClass psiClass) {
            PsiClass topClass = psiClass;
            while (topClass.getContainingClass() != null) {
                topClass = topClass.getContainingClass();
            }
            return getPackageName(topClass);
        }
        return getPackageName(psiElement);
    }

    public static @Nullable PsiJavaPackage getPackage(PsiElement element) {
        String fqn = getRealPackageName(element);
        return fqn == null ? null : JavaPsiFacade.getInstance(element.getProject()).findPackage(fqn);
    }
}
