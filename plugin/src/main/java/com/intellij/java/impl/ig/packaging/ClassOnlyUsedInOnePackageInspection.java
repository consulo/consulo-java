/*
 * Copyright 2011-2012 Bas Leijdekkers
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
import com.intellij.java.language.psi.PsiIdentifier;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Set;

public abstract class ClassOnlyUsedInOnePackageInspection extends BaseGlobalInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.classOnlyUsedInOnePackageDisplayName();
    }

    @Nullable
    @Override
    @RequiredReadAction
    public CommonProblemDescriptor[] checkElement(
        @Nonnull RefEntity refEntity,
        @Nonnull AnalysisScope scope,
        @Nonnull InspectionManager manager,
        @Nonnull GlobalInspectionContext globalContext,
        @Nonnull Object state
    ) {
        if (!(refEntity instanceof RefClass refClass)) {
            return null;
        }
        if (!(refClass.getOwner() instanceof RefPackage ownerPackage)) {
            return null;
        }
        Set<RefClass> dependencies = DependencyUtils.calculateDependenciesForClass(refClass);
        RefPackage otherPackage = null;
        for (RefClass dependency : dependencies) {
            RefPackage refPackage = RefJavaUtil.getPackage(dependency);
            if (ownerPackage == refPackage) {
                return null;
            }
            if (otherPackage != refPackage) {
                if (otherPackage == null) {
                    otherPackage = refPackage;
                }
                else {
                    return null;
                }
            }
        }
        Set<RefClass> dependents = DependencyUtils.calculateDependentsForClass(refClass);
        for (RefClass dependent : dependents) {
            RefPackage refPackage = RefJavaUtil.getPackage(dependent);
            if (ownerPackage == refPackage) {
                return null;
            }
            if (otherPackage != refPackage) {
                if (otherPackage == null) {
                    otherPackage = refPackage;
                }
                else {
                    return null;
                }
            }
        }
        if (otherPackage == null) {
            return null;
        }
        PsiClass aClass = refClass.getElement();
        PsiIdentifier identifier = aClass.getNameIdentifier();
        if (identifier == null) {
            return null;
        }
        return new CommonProblemDescriptor[]{
            manager.newProblemDescriptor(InspectionGadgetsLocalize.classOnlyUsedInOnePackageProblemDescriptor(otherPackage.getName()))
                .range(identifier)
                .create()
        };
    }
}
