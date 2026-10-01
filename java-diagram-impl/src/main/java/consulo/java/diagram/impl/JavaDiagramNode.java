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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.diagram.DiagramProvider;
import consulo.diagram.PsiDiagramNode;
import consulo.language.psi.PsiElement;

import java.util.Objects;

/**
 * @author Konstantin Bulenkov
 */
public class JavaDiagramNode extends PsiDiagramNode<PsiElement> {
    public JavaDiagramNode(PsiElement psiElement, DiagramProvider<PsiElement> provider) {
        super(psiElement, provider);
    }

    @Override
    public String getName() {
        PsiElement psiElement = getElement();
        if (psiElement instanceof PsiClass psiClass) {
            String qualifiedName = psiClass.getQualifiedName();
            return qualifiedName == null ? String.valueOf(psiClass.getName()) : qualifiedName;
        }
        if (psiElement instanceof PsiJavaPackage psiPackage) {
            return Objects.requireNonNullElse(psiPackage.getQualifiedName(), "");
        }
        return "unknown";
    }
}
