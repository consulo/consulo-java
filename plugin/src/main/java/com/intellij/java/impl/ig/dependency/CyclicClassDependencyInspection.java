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
import com.intellij.java.analysis.codeInspection.reference.RefClass;
import consulo.language.editor.inspection.reference.RefEntity;
import com.intellij.java.language.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.java.impl.ig.BaseGlobalInspection;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

public abstract class CyclicClassDependencyInspection extends BaseGlobalInspection {

  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "cyclic.class.dependency.display.name");
  }

  @Nullable
  public CommonProblemDescriptor[] checkElement(
    RefEntity refEntity,
    AnalysisScope analysisScope,
    InspectionManager inspectionManager,
    GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefClass)) {
      return null;
    }
    final RefClass refClass = (RefClass)refEntity;
    final PsiClass aClass = refClass.getElement();
    if (aClass.getContainingClass() != null) {
      return null;
    }
    final Set<RefClass> dependencies =
      DependencyUtils.calculateTransitiveDependenciesForClass(refClass);
    final Set<RefClass> dependents =
      DependencyUtils.calculateTransitiveDependentsForClass(refClass);
    final Set<RefClass> mutualDependents =
      new HashSet<RefClass>(dependencies);
    mutualDependents.retainAll(dependents);
    final int numMutualDependents = mutualDependents.size();
    if (numMutualDependents <= 1) {
      return null;
    }
    final String errorString = InspectionGadgetsBundle.message(
      "cyclic.class.dependency.problem.descriptor",
      refEntity.getName(), numMutualDependents - 1);
    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)
    };
  }
}