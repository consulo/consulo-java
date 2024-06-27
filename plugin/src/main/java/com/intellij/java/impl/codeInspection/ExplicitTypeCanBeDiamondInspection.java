/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.intention.HighPriorityAction;
import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.language.impl.psi.impl.PsiDiamondTypeUtil;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiNewExpression;
import com.intellij.java.language.psi.PsiReferenceParameterList;
import consulo.project.Project;
import consulo.language.psi.PsiElementVisitor;
import consulo.logging.Logger;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;

/**
 * User: anna
 * Date: 1/28/11
 */
@ExtensionImpl
public class ExplicitTypeCanBeDiamondInspection extends BaseJavaLocalInspectionTool {
  public static final Logger LOG = Logger.getInstance(ExplicitTypeCanBeDiamondInspection.class);

  @Nls
  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return InspectionLocalize.groupNamesLanguageLevelSpecificIssuesAndMigrationAids().get();
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return "Explicit type can be replaced with <>";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  @Override
  public String getShortName() {
    return "Convert2Diamond";
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitorImpl(@Nonnull final ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
    return new JavaElementVisitor() {
      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        if (PsiDiamondTypeUtil.canCollapseToDiamond(expression, expression, null)) {
          final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
          LOG.assertTrue(classReference != null);
          final PsiReferenceParameterList parameterList = classReference.getParameterList();
          LOG.assertTrue(parameterList != null);
          holder.registerProblem(parameterList, "Explicit type argument #ref #loc can be replaced with <>",
              ProblemHighlightType.LIKE_UNUSED_SYMBOL, new ReplaceWithDiamondFix());
        }
      }
    };
  }

  private static class ReplaceWithDiamondFix implements LocalQuickFix, HighPriorityAction {
    @Nonnull
    @Override
    public String getName() {
      return "Replace with <>";
    }

    @Nonnull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
      PsiDiamondTypeUtil.replaceExplicitWithDiamond(descriptor.getPsiElement());
    }
  }
}
