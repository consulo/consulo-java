/*
 * Copyright 2013-2026 consulo.io
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
package consulo.java.diagram.impl;

import com.intellij.java.language.psi.PsiClass;
import consulo.diagram.DiagramEdgeCreationPolicy;
import consulo.diagram.DiagramNode;
import consulo.language.psi.PsiElement;

/**
 * @author VISTALL
 * @since 2026-10-01
 */
public class JavaDiagramEdgeCreationPolicy implements DiagramEdgeCreationPolicy<PsiElement> {
    @Override
    public boolean acceptSource(DiagramNode<PsiElement> source) {
        return source.getIdentifyingElement() instanceof PsiClass psiClass && psiClass.isWritable() && psiClass.isPhysical();
    }

    @Override
    public boolean acceptTarget(DiagramNode<PsiElement> target) {
        return target.getIdentifyingElement() instanceof PsiClass;
    }
}
