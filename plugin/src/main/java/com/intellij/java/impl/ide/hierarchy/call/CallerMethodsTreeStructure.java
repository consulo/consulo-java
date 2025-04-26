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

import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.content.scope.SearchScope;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyNodeDescriptor;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyTreeStructure;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CallerMethodsTreeStructure extends HierarchyTreeStructure {
    private final String myScopeType;

    /**
     * Should be called in read action
     */
    public CallerMethodsTreeStructure(Project project, PsiMethod method, String scopeType) {
        super(project, new CallHierarchyNodeDescriptor(project, null, method, true, false));
        myScopeType = scopeType;
    }

    @Override
    @RequiredReadAction
    protected final Object[] buildChildren(HierarchyNodeDescriptor descriptor) {
        PsiMember enclosingElement = ((CallHierarchyNodeDescriptor)descriptor).getEnclosingElement();
        HierarchyNodeDescriptor nodeDescriptor = getBaseDescriptor();
        if (!(enclosingElement instanceof PsiMethod) || nodeDescriptor == null) {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }
        PsiMethod method = (PsiMethod)enclosingElement;
        PsiMethod baseMethod = (PsiMethod)((CallHierarchyNodeDescriptor)nodeDescriptor).getTargetElement();
        SearchScope searchScope = getSearchScope(myScopeType, baseMethod.getContainingClass());

        PsiClass originalClass = method.getContainingClass();
        assert originalClass != null;
        PsiClassType originalType = JavaPsiFacade.getElementFactory(myProject).createType(originalClass);
        Set<PsiMethod> methodsToFind = new HashSet<>();
        methodsToFind.add(method);
        ContainerUtil.addAll(methodsToFind, method.findDeepestSuperMethods());

        Map<PsiMember, CallHierarchyNodeDescriptor> methodToDescriptorMap = new HashMap<>();
        for (PsiMethod methodToFind : methodsToFind) {
            MethodReferencesSearch.search(methodToFind, searchScope, true).forEach(reference -> {
                if (reference instanceof PsiReferenceExpression refExpr) {
                    PsiExpression qualifier = refExpr.getQualifierExpression();
                    if (qualifier instanceof PsiSuperExpression) {
                        // filter super.foo() call inside foo() and similar cases (bug 8411)
                        PsiClass superClass = PsiUtil.resolveClassInType(qualifier.getType());
                        if (originalClass.isInheritor(superClass, true)) {
                            return true;
                        }
                    }
                    if (qualifier != null && !methodToFind.isStatic()) {
                        PsiType qualifierType = qualifier.getType();
                        if (qualifierType instanceof PsiClassType classType &&
                            !TypeConversionUtil.isAssignable(classType, originalType) && methodToFind != method) {
                            PsiClass psiClass = classType.resolve();
                            if (psiClass != null) {
                                PsiMethod callee = psiClass.findMethodBySignature(methodToFind, true);
                                if (callee != null && !methodsToFind.contains(callee)) {
                                    // skip sibling methods
                                    return true;
                                }
                            }
                        }
                    }
                }
                else {
                    if (!(reference instanceof PsiElement element)) {
                        return true;
                    }

                    PsiElement parent = element.getParent();
                    if (parent instanceof PsiNewExpression newExpr) {
                        if (newExpr.getClassReference() != reference) {
                            return true;
                        }
                    }
                    else if (parent instanceof PsiAnonymousClass anonymousClass) {
                        if (anonymousClass.getBaseClassReference() != reference) {
                            return true;
                        }
                    }
                    else {
                        return true;
                    }
                }

                PsiElement element = reference.getElement();
                PsiMember key = CallHierarchyNodeDescriptor.getEnclosingElement(element);

                synchronized (methodToDescriptorMap) {
                    CallHierarchyNodeDescriptor d = methodToDescriptorMap.get(key);
                    if (d == null) {
                        d = new CallHierarchyNodeDescriptor(myProject, descriptor, element, false, true);
                        methodToDescriptorMap.put(key, d);
                    }
                    else if (!d.hasReference(reference)) {
                        d.incrementUsageCount();
                    }
                    d.addReference(reference);
                }
                return true;
            });
        }

        return methodToDescriptorMap.values().toArray(new Object[methodToDescriptorMap.size()]);
    }

    @Override
    public boolean isAlwaysShowPlus() {
        return true;
    }
}
