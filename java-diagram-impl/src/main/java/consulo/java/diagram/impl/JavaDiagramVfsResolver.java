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
import consulo.diagram.DiagramVfsResolver;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class JavaDiagramVfsResolver implements DiagramVfsResolver<PsiElement> {
    @Override
    public String getQualifiedName(PsiElement element) {
        String fqn = JavaDiagramUtil.getFQN(element);
        return fqn == null ? "" : fqn;
    }

    @Override
    public @Nullable PsiElement resolveElementByFQN(String fqn, Project project) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass psiClass = facade.findClass(fqn, GlobalSearchScope.allScope(project));
        return psiClass == null ? facade.findPackage(fqn) : psiClass;
    }
}
