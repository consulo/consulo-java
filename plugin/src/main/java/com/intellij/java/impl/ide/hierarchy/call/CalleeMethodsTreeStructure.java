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
package com.intellij.java.impl.ide.hierarchy.call;

import consulo.annotation.access.RequiredReadAction;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.java.language.psi.*;
import consulo.project.Project;
import consulo.language.psi.*;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import consulo.util.collection.ArrayUtil;

import java.util.ArrayList;
import java.util.HashMap;

public final class CalleeMethodsTreeStructure extends HierarchyTreeStructure {
    private final String myScopeType;

    /**
     * Should be called in read action
     */
    public CalleeMethodsTreeStructure(Project project, PsiMethod method, String scopeType) {
        super(project, new CallHierarchyNodeDescriptor(project, null, method, true, false));
        myScopeType = scopeType;
    }

    @Override
    @RequiredReadAction
    protected final Object[] buildChildren(HierarchyNodeDescriptor descriptor) {
        PsiMember enclosingElement = ((CallHierarchyNodeDescriptor)descriptor).getEnclosingElement();
        if (!(enclosingElement instanceof PsiMethod method)) {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }

        ArrayList<PsiMethod> methods = new ArrayList<>();

        PsiCodeBlock body = method.getBody();
        if (body != null) {
            visitor(body, methods);
        }

        PsiMethod baseMethod = (PsiMethod)((CallHierarchyNodeDescriptor)getBaseDescriptor()).getTargetElement();
        PsiClass baseClass = baseMethod.getContainingClass();

        HashMap<PsiMethod, CallHierarchyNodeDescriptor> methodToDescriptorMap = new HashMap<>();

        ArrayList<CallHierarchyNodeDescriptor> result = new ArrayList<>();

        for (PsiMethod calledMethod : methods) {
            if (!isInScope(baseClass, calledMethod, myScopeType)) {
                continue;
            }

            CallHierarchyNodeDescriptor d = methodToDescriptorMap.get(calledMethod);
            if (d == null) {
                d = new CallHierarchyNodeDescriptor(myProject, descriptor, calledMethod, false, false);
                methodToDescriptorMap.put(calledMethod, d);
                result.add(d);
            }
            else {
                d.incrementUsageCount();
            }
        }

        // also add overriding methods as children
        PsiMethod[] overridingMethods = OverridingMethodsSearch.search(method, true).toArray(PsiMethod.EMPTY_ARRAY);
        for (PsiMethod overridingMethod : overridingMethods) {
            if (!isInScope(baseClass, overridingMethod, myScopeType)) {
                continue;
            }
            CallHierarchyNodeDescriptor node =
                new CallHierarchyNodeDescriptor(myProject, descriptor, overridingMethod, false, false);
            if (!result.contains(node)) {
                result.add(node);
            }
        }

        /*
        // show method implementations in EJB Class
        PsiMethod[] ejbImplementations = EjbUtil.findEjbImplementations(method, null);
        for (int i = 0; i < ejbImplementations.length; i++) {
            PsiMethod ejbImplementation = ejbImplementations[i];
            result.add(new CallHierarchyNodeDescriptor(myProject, descriptor, ejbImplementation, false));
        }
        */
        return ArrayUtil.toObjectArray(result);
    }

    @RequiredReadAction
    private static void visitor(PsiElement element, ArrayList<PsiMethod> methods) {
        PsiElement[] children = element.getChildren();
        for (PsiElement child : children) {
            visitor(child, methods);
            if (child instanceof PsiMethodCallExpression methodCall) {
                if (methodCall.getMethodExpression().resolve() instanceof PsiMethod method) {
                    methods.add(method);
                }
            }
            else if (child instanceof PsiNewExpression newExpr) {
                PsiMethod method = newExpr.resolveConstructor();
                if (method != null) {
                    methods.add(method);
                }
            }
        }
    }
}
