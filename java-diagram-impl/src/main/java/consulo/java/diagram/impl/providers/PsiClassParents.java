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
package consulo.java.diagram.impl.providers;

import com.intellij.java.language.psi.PsiClass;
import consulo.diagram.providers.SupersProvider;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class PsiClassParents extends SupersProvider<PsiElement> {
    public static Set<PsiClass> getSupers(@Nullable PsiClass child) {
        Set<PsiClass> supers = new HashSet<>();
        if (child == null) {
            return supers;
        }
        for (PsiClass psiClass : child.getSupers()) {
            supers.add(psiClass);
            supers.addAll(getSupers(psiClass));
        }
        return supers;
    }

    @Override
    public PsiElement[] getElements(PsiElement element, Project project) {
        if (element instanceof PsiClass psiClass) {
            return getSupers(psiClass).toArray(PsiClass.EMPTY_ARRAY);
        }
        return PsiClass.EMPTY_ARRAY;
    }

    @Override
    public String getHeaderName(PsiElement element, Project project) {
        return "Super classes for " + (element instanceof PsiClass psiClass ? psiClass.getName() : "");
    }

    @Override
    public Comparator<? super PsiElement> getComparator() {
        return PSI_COMPARATOR;
    }
}
