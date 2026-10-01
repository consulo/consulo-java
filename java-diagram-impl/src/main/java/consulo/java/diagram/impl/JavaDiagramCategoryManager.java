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

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.application.AllIcons;
import consulo.diagram.AbstractDiagramNodeContentManager;
import consulo.diagram.DiagramCategory;

/**
 * @author Konstantin Bulenkov
 */
public class JavaDiagramCategoryManager extends AbstractDiagramNodeContentManager {
    public static final DiagramCategory FIELDS = new DiagramCategory("Fields", AllIcons.Nodes.Field);
    public static final DiagramCategory CONSTRUCTORS = new DiagramCategory("Constructors", AllIcons.Nodes.ClassInitializer);
    public static final DiagramCategory METHODS = new DiagramCategory("Methods", AllIcons.Nodes.Method);
    public static final DiagramCategory PROPERTIES = new DiagramCategory("Properties", AllIcons.Nodes.Property);

    private static final DiagramCategory[] CATEGORIES = {FIELDS, CONSTRUCTORS, METHODS, PROPERTIES};

    public JavaDiagramCategoryManager() {
        setEnabled(FIELDS, true);
        setEnabled(CONSTRUCTORS, true);
        setEnabled(METHODS, true);
    }

    @Override
    public DiagramCategory[] getContentCategories() {
        return CATEGORIES;
    }

    @Override
    public boolean isInCategory(Object element, DiagramCategory category) {
        if (FIELDS.equals(category)) {
            return element instanceof PsiField;
        }
        if (CONSTRUCTORS.equals(category)) {
            return element instanceof PsiMethod method && method.isConstructor();
        }
        if (METHODS.equals(category)) {
            return element instanceof PsiMethod method && !method.isConstructor();
        }
        return false;
    }
}
