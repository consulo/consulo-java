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
package com.intellij.java.impl.ide.hierarchy.type;

import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.project.Project;
import consulo.java.language.module.util.JavaClassNames;
import com.intellij.java.language.psi.PsiClass;

import java.util.ArrayList;

public final class TypeHierarchyTreeStructure extends SubtypesHierarchyTreeStructure {
    public TypeHierarchyTreeStructure(Project project, PsiClass aClass, String currentScopeType) {
        super(project, buildHierarchyElement(project, aClass), currentScopeType);
        setBaseElement(myBaseDescriptor); //to set myRoot
    }

    private static HierarchyNodeDescriptor buildHierarchyElement(Project project, PsiClass aClass) {
        HierarchyNodeDescriptor descriptor = null;
        PsiClass[] superClasses = createSuperClasses(aClass);
        for (int i = superClasses.length - 1; i >= 0; i--) {
            PsiClass superClass = superClasses[i];
            HierarchyNodeDescriptor newDescriptor = new TypeHierarchyNodeDescriptor(project, descriptor, superClass, false);
            if (descriptor != null) {
                descriptor.setCachedChildren(new HierarchyNodeDescriptor[]{newDescriptor});
            }
            descriptor = newDescriptor;
        }
        HierarchyNodeDescriptor newDescriptor = new TypeHierarchyNodeDescriptor(project, descriptor, aClass, true);
        if (descriptor != null) {
            descriptor.setCachedChildren(new HierarchyNodeDescriptor[]{newDescriptor});
        }
        return newDescriptor;
    }

    private static PsiClass[] createSuperClasses(PsiClass aClass) {
        if (!aClass.isValid()) {
            return PsiClass.EMPTY_ARRAY;
        }
        if (aClass.isInterface()) {
            return PsiClass.EMPTY_ARRAY;
        }

        ArrayList<PsiClass> superClasses = new ArrayList<>();
        while (!JavaClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName())) {
            PsiClass aClass1 = aClass;
            PsiClass[] superTypes = aClass1.getSupers();
            PsiClass superType = null;
            for (PsiClass type : superTypes) {
                if (!type.isInterface()) {
                    superType = type;
                    break;
                }
            }
            if (superType == null) {
                break;
            }
            if (superClasses.contains(superType)) {
                break;
            }
            superClasses.add(superType);
            aClass = superType;
        }

        return superClasses.toArray(new PsiClass[superClasses.size()]);
    }
}
