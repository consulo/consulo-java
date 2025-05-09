/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.ide.hierarchy.method;

import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.FunctionalExpressionSearch;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyBrowserManager;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyTreeStructure;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class MethodHierarchyTreeStructure extends HierarchyTreeStructure {
    private final SmartPsiElementPointer myMethod;

    /**
     * Should be called in read action
     */
    public MethodHierarchyTreeStructure(Project project, PsiMethod method) {
        super(project, null);
        myBaseDescriptor = buildHierarchyElement(project, method);
        ((MethodHierarchyNodeDescriptor)myBaseDescriptor).setTreeStructure(this);
        myMethod = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(method);
        setBaseElement(myBaseDescriptor); //to set myRoot
    }

    private HierarchyNodeDescriptor buildHierarchyElement(Project project, PsiMethod method) {
        PsiClass suitableBaseClass = findSuitableBaseClass(method);

        HierarchyNodeDescriptor descriptor = null;
        ArrayList<PsiClass> superClasses = createSuperClasses(suitableBaseClass);

        if (!suitableBaseClass.equals(method.getContainingClass())) {
            superClasses.add(0, suitableBaseClass);
        }

        // remove from the top of the branch the classes that contain no 'method'
        for (int i = superClasses.size() - 1; i >= 0; i--) {
            PsiClass psiClass = superClasses.get(i);

            if (MethodHierarchyUtil.findBaseMethodInClass(method, psiClass, false) == null) {
                superClasses.remove(i);
            }
            else {
                break;
            }
        }

        for (int i = superClasses.size() - 1; i >= 0; i--) {
            PsiClass superClass = superClasses.get(i);
            HierarchyNodeDescriptor newDescriptor =
                new MethodHierarchyNodeDescriptor(project, descriptor, superClass, false, this);
            if (descriptor != null) {
                descriptor.setCachedChildren(new HierarchyNodeDescriptor[]{newDescriptor});
            }
            descriptor = newDescriptor;
        }
        HierarchyNodeDescriptor newDescriptor =
            new MethodHierarchyNodeDescriptor(project, descriptor, method.getContainingClass(), true, this);
        if (descriptor != null) {
            descriptor.setCachedChildren(new HierarchyNodeDescriptor[]{newDescriptor});
        }
        return newDescriptor;
    }

    private static ArrayList<PsiClass> createSuperClasses(PsiClass aClass) {
        if (!aClass.isValid()) {
            return new ArrayList<>();
        }

        ArrayList<PsiClass> superClasses = new ArrayList<>();
        while (!isJavaLangObject(aClass)) {
            PsiClass aClass1 = aClass;
            PsiClass[] superTypes = aClass1.getSupers();
            PsiClass superType = null;
            // find class first
            for (PsiClass type : superTypes) {
                if (!type.isInterface() && !isJavaLangObject(type)) {
                    superType = type;
                    break;
                }
            }
            // if we haven't found a class, try to find an interface
            if (superType == null) {
                for (PsiClass type : superTypes) {
                    if (!isJavaLangObject(type)) {
                        superType = type;
                        break;
                    }
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

        return superClasses;
    }

    private static boolean isJavaLangObject(PsiClass aClass) {
        return CommonClassNames.JAVA_LANG_OBJECT.equals(aClass.getQualifiedName());
    }

    private static PsiClass findSuitableBaseClass(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();

        if (containingClass instanceof PsiAnonymousClass) {
            return containingClass;
        }

        PsiClass superClass = containingClass.getSuperClass();
        if (superClass == null) {
            return containingClass;
        }

        if (MethodHierarchyUtil.findBaseMethodInClass(method, superClass, true) == null) {
            for (PsiClass anInterface : containingClass.getInterfaces()) {
                if (MethodHierarchyUtil.findBaseMethodInClass(method, anInterface, true) != null) {
                    return anInterface;
                }
            }
        }

        return containingClass;
    }

    @Nullable
    @RequiredReadAction
    public final PsiMethod getBaseMethod() {
        return myMethod.getElement() instanceof PsiMethod method ? method : null;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected final Object[] buildChildren(@Nonnull HierarchyNodeDescriptor descriptor) {
        PsiElement psiElement = ((MethodHierarchyNodeDescriptor)descriptor).getPsiClass();
        if (!(psiElement instanceof PsiClass psiClass)) {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }
        Collection<PsiClass> subclasses = getSubclasses(psiClass);

        List<HierarchyNodeDescriptor> descriptors = new ArrayList<>(subclasses.size());
        for (PsiClass aClass : subclasses) {
            HierarchyBrowserManager.State state = HierarchyBrowserManager.getInstance(myProject).getState();
            assert state != null;
            if (state.HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED && shouldHideClass(aClass)) {
                continue;
            }

            MethodHierarchyNodeDescriptor d = new MethodHierarchyNodeDescriptor(myProject, descriptor, aClass, false, this);
            descriptors.add(d);
        }

        PsiMethod existingMethod = ((MethodHierarchyNodeDescriptor)descriptor).getMethod(psiClass, false);
        if (existingMethod != null) {
            FunctionalExpressionSearch.search(existingMethod).forEach(expression -> {
                descriptors.add(new MethodHierarchyNodeDescriptor(
                    myProject,
                    descriptor,
                    expression,
                    false,
                    MethodHierarchyTreeStructure.this
                ));
                return true;
            });
        }

        return descriptors.toArray(new HierarchyNodeDescriptor[descriptors.size()]);
    }

    private static Collection<PsiClass> getSubclasses(PsiClass psiClass) {
        if (psiClass instanceof PsiAnonymousClass || psiClass.isFinal()) {
            return Collections.emptyList();
        }

        return ClassInheritorsSearch.search(psiClass, false).findAll();
    }

    @RequiredReadAction
    private boolean shouldHideClass(PsiClass psiClass) {
        if (getMethod(psiClass, false) != null || isSuperClassForBaseClass(psiClass)) {
            return false;
        }

        if (hasBaseClassMethod(psiClass) || isAbstract(psiClass)) {
            for (PsiClass subclass : getSubclasses(psiClass)) {
                if (!shouldHideClass(subclass)) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private boolean isAbstract(PsiModifierListOwner owner) {
        return owner.hasModifierProperty(PsiModifier.ABSTRACT);
    }

    @RequiredReadAction
    private boolean hasBaseClassMethod(PsiClass psiClass) {
        PsiMethod baseClassMethod = getMethod(psiClass, true);
        return baseClassMethod != null && !isAbstract(baseClassMethod);
    }

    @RequiredReadAction
    private PsiMethod getMethod(PsiClass aClass, boolean checkBases) {
        return MethodHierarchyUtil.findBaseMethodInClass(getBaseMethod(), aClass, checkBases);
    }

    @RequiredReadAction
    boolean isSuperClassForBaseClass(PsiClass aClass) {
        PsiMethod baseMethod = getBaseMethod();
        if (baseMethod == null) {
            return false;
        }
        PsiClass baseClass = baseMethod.getContainingClass();
        if (baseClass == null) {
            return false;
        }
        // NB: parameters here are at CORRECT places!!!
        return baseClass.isInheritor(aClass, true);
    }
}
