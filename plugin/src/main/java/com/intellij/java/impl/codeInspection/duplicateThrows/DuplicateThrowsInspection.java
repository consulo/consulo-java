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
package com.intellij.java.impl.codeInspection.duplicateThrows;

import com.intellij.java.analysis.codeInspection.GroupNames;
import com.intellij.java.impl.codeInspection.DeleteThrowsFix;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import com.intellij.java.analysis.impl.codeInspection.ex.BaseLocalInspectionTool;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class DuplicateThrowsInspection extends BaseLocalInspectionTool {

  @SuppressWarnings("PublicField")
  public boolean ignoreSubclassing = false;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.duplicate.throws.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @jakarta.annotation.Nonnull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  @Override
  @jakarta.annotation.Nonnull
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
  @jakarta.annotation.Nonnull
  public PsiElementVisitor buildVisitorImpl(@jakarta.annotation.Nonnull final ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
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
