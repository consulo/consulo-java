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

import consulo.application.AllIcons;
import consulo.diagram.DiagramCategory;
import consulo.diagram.DiagramRelationshipInfo;
import consulo.diagram.DiagramRelationshipManager;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class JavaDiagramRelationshipManager implements DiagramRelationshipManager<PsiElement> {
    private static final DiagramCategory[] CATEGORIES = {new DiagramCategory("Dependencies", AllIcons.Nodes.Parameter)};

    @Override
    public @Nullable DiagramRelationshipInfo getDependencyInfo(PsiElement e1, PsiElement e2, DiagramCategory category) {
        return null;
    }

    @Override
    public DiagramCategory[] getContentCategories() {
        return CATEGORIES;
    }
}
