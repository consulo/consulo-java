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
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.deadCodeNotWorking.impl.SingleIntegerFieldOptionsPanel;
import consulo.language.editor.inspection.CommonProblemDescriptor;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.reference.RefEntity;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.Set;

public abstract class ClassWithTooManyTransitiveDependenciesInspection
  extends BaseGlobalInspection {

  @SuppressWarnings({"PublicField"})
  public int limit = 35;

  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsLocalize.classWithTooManyTransitiveDependenciesDisplayName().get();
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
    final String errorString =
      InspectionGadgetsLocalize.classWithTooManyTransitiveDependenciesProblemDescriptor(refEntity.getName(), numDependencies, limit).get();
    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)
    };
  }

  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.classWithTooManyTransitiveDependenciesMaxOption();
    return new SingleIntegerFieldOptionsPanel(message.get(), this, "limit");
  }
}