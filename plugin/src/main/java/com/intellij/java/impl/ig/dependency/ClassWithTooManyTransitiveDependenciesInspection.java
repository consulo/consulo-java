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

import com.intellij.java.analysis.codeInspection.reference.RefClass;
import com.intellij.java.impl.ig.BaseGlobalInspection;
import com.intellij.java.language.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.ClassUtils;
import consulo.language.editor.inspection.CommonProblemDescriptor;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.deadCodeNotWorking.impl.SingleIntegerFieldOptionsPanel;
import consulo.language.editor.scope.AnalysisScope;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.util.Set;

public abstract class ClassWithTooManyTransitiveDependenciesInspection
  extends BaseGlobalInspection {

  @SuppressWarnings({"PublicField"})
  public int limit = 35;

  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "class.with.too.many.transitive.dependencies.display.name");
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
    if (ClassUtils.isInnerClass(aClass)) {
      return null;
    }
    final Set<RefClass> dependencies =
      DependencyUtils.calculateTransitiveDependenciesForClass(refClass);
    final int numDependencies = dependencies.size();
    if (numDependencies <= limit) {
      return null;
    }
    final String errorString = InspectionGadgetsBundle.message(
      "class.with.too.many.transitive.dependencies.problem.descriptor",
      refEntity.getName(), numDependencies, limit);
    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)
    };
  }

  public JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(
      InspectionGadgetsBundle.message(
        "class.with.too.many.transitive.dependencies.max.option"),
      this, "limit");
  }
}