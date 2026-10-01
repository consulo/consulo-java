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

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import consulo.diagram.DiagramDnDProvider;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class JavaDiagramDnDSupport implements DiagramDnDProvider<PsiElement> {
    @Override
    public boolean isAcceptedForDnD(Object o, Project project) {
        return o instanceof PsiClass
            || o instanceof PsiDirectory directory && JavaDirectoryService.getInstance().getPackage(directory) != null;
    }

    @Override
    public @Nullable PsiElement wrapToModelObject(Object o, Project project) {
        if (o instanceof PsiClass psiClass) {
            return psiClass;
        }
        return o instanceof PsiDirectory directory ? JavaDirectoryService.getInstance().getPackage(directory) : null;
    }
}
