/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection.duplicateThrows;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.DeleteThrowsFix;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.analysis.impl.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.java.language.psi.*;
import com.intellij.psi.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.swing.*;

public class DuplicateThrowsInspection extends BaseLocalInspectionTool {

  @SuppressWarnings("PublicField")
  public boolean ignoreSubclassing = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.duplicate.throws.display.name");
  }

  @Override
  @Nonnull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @Nonnull
  public String getShortName() {
    return "DuplicateThrows";
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      InspectionsBundle.message("inspection.duplicate.throws.ignore.subclassing.option"), this, "ignoreSubclassing");
  }

  @Override
  @Nonnull
  public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override public void visitMethod(PsiMethod method) {
        PsiReferenceList throwsList = method.getThrowsList();
        PsiJavaCodeReferenceElement[] refs = throwsList.getReferenceElements();
        PsiClassType[] types = throwsList.getReferencedTypes();
        for (int i = 0; i < types.length; i++) {
          PsiClassType type = types[i];
          for (int j = i+1; j < types.length; j++) {
            PsiClassType otherType = types[j];
            String problem = null;
            PsiJavaCodeReferenceElement ref = refs[i];
            if (type.equals(otherType)) {
              problem = InspectionsBundle.message("inspection.duplicate.throws.problem");
            }
            else if (!ignoreSubclassing) {
              if (otherType.isAssignableFrom(type)) {
                problem = InspectionsBundle.message("inspection.duplicate.throws.more.general.problem", otherType.getCanonicalText());
              }
              else if (type.isAssignableFrom(otherType)) {
                problem = InspectionsBundle.message("inspection.duplicate.throws.more.general.problem", type.getCanonicalText());
                ref = refs[j];
                type = otherType;
              }
            }
            if (problem != null) {
              holder.registerProblem(ref, problem, ProblemHighlightType.LIKE_UNUSED_SYMBOL, new DeleteThrowsFix(method, type));
            }
          }
        }
      }
    };
  }
}
