/*
 * Copyright 2006-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.packaging;

import com.intellij.java.analysis.codeInspection.reference.RefClass;
import com.intellij.java.analysis.codeInspection.reference.RefJavaUtil;
import com.intellij.java.analysis.codeInspection.reference.RefPackage;
import com.intellij.java.impl.ig.BaseGlobalInspection;
import com.intellij.java.impl.ig.dependency.DependencyUtils;
import com.intellij.java.language.psi.PsiClass;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.language.editor.inspection.CommonProblemDescriptor;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class DisjointPackageInspection extends BaseGlobalInspection {

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.disjointPackageDisplayName();
    }

    @Override
    @Nullable
    public CommonProblemDescriptor[] checkElement(
        RefEntity refEntity, AnalysisScope analysisScope,
        InspectionManager inspectionManager,
        GlobalInspectionContext globalInspectionContext,
        Object state) {
        if (!(refEntity instanceof RefPackage)) {
            return null;
        }
        RefPackage refPackage = (RefPackage) refEntity;
        List<RefEntity> children = refPackage.getChildren();
        if (children == null) {
            return null;
        }
        Set<RefClass> childClasses = new HashSet<RefClass>();
        for (RefEntity child : children) {
            if (!(child instanceof RefClass)) {
                continue;
            }
            PsiClass psiClass = ((RefClass) child).getElement();
            if (ClassUtils.isInnerClass(psiClass)) {
                continue;
            }
            childClasses.add((RefClass) child);
        }
        if (childClasses.isEmpty()) {
            return null;
        }
        Set<Set<RefClass>> components =
            createComponents(refPackage, childClasses);
        if (components.size() == 1) {
            return null;
        }
        String errorString = InspectionGadgetsLocalize.disjointPackageProblemDescriptor(
            refPackage.getQualifiedName(),
            components.size()
        ).get();

        return new CommonProblemDescriptor[]{
            inspectionManager.createProblemDescriptor(errorString)
        };
    }

    private static Set<Set<RefClass>> createComponents(
        RefPackage aPackage, Set<RefClass> classes) {
        Set<RefClass> allClasses = new HashSet<RefClass>(classes);
        Set<Set<RefClass>> out = new HashSet<Set<RefClass>>();
        while (!allClasses.isEmpty()) {
            RefClass seed = allClasses.iterator().next();
            allClasses.remove(seed);
            Set<RefClass> currentComponent = new HashSet<RefClass>();
            currentComponent.add(seed);
            List<RefClass> pendingClasses = new ArrayList<RefClass>();
            pendingClasses.add(seed);
            while (!pendingClasses.isEmpty()) {
                RefClass classToProcess = pendingClasses.remove(0);
                Set<RefClass> relatedClasses =
                    getRelatedClasses(aPackage, classToProcess);
                for (RefClass relatedClass : relatedClasses) {
                    if (!currentComponent.contains(relatedClass) &&
                        !pendingClasses.contains(relatedClass)) {
                        currentComponent.add(relatedClass);
                        pendingClasses.add(relatedClass);
                        allClasses.remove(relatedClass);
                    }
                }
            }
            out.add(currentComponent);
        }
        return out;
    }

    private static Set<RefClass> getRelatedClasses(RefPackage aPackage,
                                                   RefClass classToProcess) {
        Set<RefClass> out = new HashSet<RefClass>();
        Set<RefClass> dependencies =
            DependencyUtils.calculateDependenciesForClass(classToProcess);
        for (RefClass dependency : dependencies) {
            if (packageContainsClass(aPackage, dependency)) {
                out.add(dependency);
            }
        }

        Set<RefClass> dependents =
            DependencyUtils.calculateDependentsForClass(classToProcess);
        for (RefClass dependent : dependents) {
            if (packageContainsClass(aPackage, dependent)) {
                out.add(dependent);
            }
        }
        return out;
    }

    private static boolean packageContainsClass(RefPackage aPackage,
                                                RefClass aClass) {
        return aPackage.equals(RefJavaUtil.getPackage(aClass));
    }
}
