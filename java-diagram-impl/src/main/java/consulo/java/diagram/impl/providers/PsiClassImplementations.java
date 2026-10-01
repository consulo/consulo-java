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

import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
import consulo.diagram.providers.ImplementationsProvider;
import consulo.language.psi.PsiElement;
import consulo.project.Project;

import java.util.Comparator;

/**
 * @author Konstantin Bulenkov
 */
public class PsiClassImplementations extends ImplementationsProvider<PsiElement> {
    @Override
    public PsiElement[] getElements(PsiElement element, Project project) {
        if (!(element instanceof PsiClass psiClass) || CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) {
            return PsiClass.EMPTY_ARRAY;
        }
        return ClassInheritorsSearch.search(psiClass, true).toArray(PsiClass.EMPTY_ARRAY);
    }

    @Override
    public String getHeaderName(PsiElement element, Project project) {
        return "Implementations of " + (element instanceof PsiClass psiClass ? psiClass.getName() : "");
    }

    @Override
    public Comparator<? super PsiElement> getComparator() {
        return PSI_COMPARATOR;
    }
}
