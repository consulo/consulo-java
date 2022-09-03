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

import consulo.language.editor.scope.AnalysisScope;
import consulo.language.editor.inspection.CommonProblemDescriptor;
import consulo.language.editor.inspection.GlobalInspectionContext;
import consulo.language.editor.inspection.scheme.InspectionManager;
import com.intellij.java.analysis.codeInspection.reference.RefClass;
import consulo.language.editor.inspection.reference.RefEntity;
import com.intellij.java.analysis.codeInspection.reference.RefPackage;
import consulo.ide.impl.idea.codeInspection.ui.SingleIntegerFieldOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.java.impl.ig.BaseGlobalInspection;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.swing.*;
import java.util.List;

public class PackageWithTooFewClassesInspection extends BaseGlobalInspection {

  @SuppressWarnings({"PublicField"})
  public int limit = 3;

  @Nonnull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "package.with.too.few.classes.display.name");
  }

  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(
    RefEntity refEntity,
    AnalysisScope analysisScope,
    InspectionManager inspectionManager,
    GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefPackage)) {
      return null;
    }
    final List<RefEntity> children = refEntity.getChildren();
    if (children == null) {
      return null;
    }
    int numClasses = 0;
    for (RefEntity child : children) {
      if (child instanceof RefClass) {
        numClasses++;
      }
    }
    if (numClasses >= limit || numClasses == 0) {
      return null;
    }
    final String errorString = InspectionGadgetsBundle.message(
      "package.with.too.few.classes.problem.descriptor",
      refEntity.getQualifiedName(), Integer.valueOf(numClasses),
      Integer.valueOf(limit));
    return new CommonProblemDescriptor[]{
      inspectionManager.createProblemDescriptor(errorString)
    };
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(
      InspectionGadgetsBundle.message(
        "package.with.too.few.classes.min.option"),
      this, "limit");
  }
}