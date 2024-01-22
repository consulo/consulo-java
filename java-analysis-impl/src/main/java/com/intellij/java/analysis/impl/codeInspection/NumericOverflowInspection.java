/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection;

import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.analysis.codeInspection.GroupNames;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.util.ConstantEvaluationOverflowException;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;

/**
 * User: cdr
 */
@ExtensionImpl
public class NumericOverflowInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Key<String> HAS_OVERFLOW_IN_CHILD = Key.create("HAS_OVERFLOW_IN_CHILD");

  @Nls
  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.NUMERIC_GROUP_NAME;
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return "Numeric overflow";
  }

  @Nonnull
  @Override
  public String getShortName() {
    return "NumericOverflow";
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiElementVisitor buildVisitorImpl(@Nonnull final ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
    return new JavaElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitExpression(expression);
      }

      @Override
      public void visitExpression(PsiExpression expression) {
        boolean info = hasOverflow(expression, holder.getProject());
        if (info) {
          holder.registerProblem(expression, JavaErrorBundle.message("numeric.overflow.in.expression"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }
    };
  }

  private static boolean hasOverflow(PsiExpression expr, @Nonnull Project project) {
    if (!TypeConversionUtil.isNumericType(expr.getType())) return false;
    boolean overflow = false;
    try {
      if (expr.getUserData(HAS_OVERFLOW_IN_CHILD) == null) {
        JavaPsiFacade.getInstance(project).getConstantEvaluationHelper().computeConstantExpression(expr, true);
      } else {
        overflow = true;
      }
    } catch (ConstantEvaluationOverflowException e) {
      overflow = true;
    } finally {
      PsiElement parent = expr.getParent();
      if (overflow && parent instanceof PsiExpression) {
        parent.putUserData(HAS_OVERFLOW_IN_CHILD, "");
      }
    }

    return overflow;
  }

}
