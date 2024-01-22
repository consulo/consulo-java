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
package com.intellij.java.impl.ig.dependency;

import consulo.language.editor.scope.AnalysisScope;
import consulo.language.editor.inspection.CommonProblemDescriptor;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.inspection.reference.RefEntity;
import com.intellij.java.analysis.codeInspection.reference.RefPackage;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.java.impl.ig.BaseGlobalInspection;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

public abstract class CyclicPackageDependencyInspection extends BaseGlobalInspection {

  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "cyclic.package.dependency.display.name");
  }

  @Nullable
  public CommonProblemDescriptor[] checkElement(
    RefEntity refEntity,
    AnalysisScope analysisScope,
    InspectionManager inspectionManager,
    GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefPackage)) {
      return null;
    }
    final RefPackage refPackage = (RefPackage)refEntity;
    final Set<RefPackage> dependencies =
      DependencyUtils.calculateTransitiveDependenciesForPackage(refPackage);
    final Set<RefPackage> dependents =
      DependencyUtils.calculateTransitiveDependentsForPackage(refPackage);
    final Set<RefPackage> mutualDependents =
      new HashSet<RefPackage>(dependencies);
    mutualDependents.retainAll(dependents);
    final int numMutualDependents = mutualDependents.size();
    if (numMutualDependents <= 1) {
      return null;
    }
    final String packageName = refEntity.getName();
    final String errorString = InspectionGadgetsBundle.message(
      "cyclic.package.dependency.problem.descriptor",
      packageName, numMutualDependents - 1);
    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)
    };
  }
}