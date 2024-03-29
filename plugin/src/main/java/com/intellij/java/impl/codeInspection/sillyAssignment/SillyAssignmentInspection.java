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
package com.intellij.java.impl.codeInspection.sillyAssignment;

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;

/**
 * User: anna
 * Date: 15-Nov-2005
 */
@ExtensionImpl
public class SillyAssignmentInspection extends BaseJavaLocalInspectionTool {
  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.variable.assigned.to.itself.display.name");
  }

  @Override
  @Nonnull
  @NonNls
  public String getShortName() {
    return "SillyAssignment";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  public PsiElementVisitor buildVisitorImpl(@Nonnull final ProblemsHolder holder,
                                            boolean isOnTheFly,
                                            LocalInspectionToolSession session,
                                            Object state) {
    return new JavaElementVisitor() {

      @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        checkSillyAssignment(expression, holder);
      }

      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitElement(expression);
      }

      @Override public void visitVariable(final PsiVariable variable) {
        final PsiExpression initializer = PsiUtil.deparenthesizeExpression(variable.getInitializer());
        if (initializer instanceof PsiAssignmentExpression) {
          final PsiExpression lExpr = PsiUtil.deparenthesizeExpression(((PsiAssignmentExpression)initializer).getLExpression());
          checkExpression(variable, lExpr);
        }
        else {
          checkExpression(variable, initializer);
        }
      }

      private void checkExpression(PsiVariable variable, PsiExpression expression) {
        if (!(expression instanceof PsiReferenceExpression)) {
          return;
        }
        final PsiReferenceExpression refExpr = (PsiReferenceExpression)expression;
        final PsiExpression qualifier = refExpr.getQualifierExpression();
        if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression ||
            variable.hasModifierProperty(PsiModifier.STATIC)) {
          if (refExpr.isReferenceTo(variable)) {
            holder.registerProblem(expression, InspectionsBundle.message("assignment.to.declared.variable.problem.descriptor",
                                                                         variable.getName()), ProblemHighlightType.LIKE_UNUSED_SYMBOL);
          }
        }
      }
    };
  }

  private static void checkSillyAssignment(PsiAssignmentExpression assignment, ProblemsHolder holder) {
    if (assignment.getOperationTokenType() != JavaTokenType.EQ) return;
    PsiExpression lExpression = assignment.getLExpression();
    PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return;
    lExpression = PsiUtil.deparenthesizeExpression(lExpression);
    rExpression = PsiUtil.deparenthesizeExpression(rExpression);
    if (!(lExpression instanceof PsiReferenceExpression)) return;
    PsiReferenceExpression rRef;
    if (!(rExpression instanceof PsiReferenceExpression)) {
      if (!(rExpression instanceof PsiAssignmentExpression)) return;
      final PsiAssignmentExpression rAssignmentExpression = (PsiAssignmentExpression)rExpression;
      final PsiExpression assignee = PsiUtil.deparenthesizeExpression(rAssignmentExpression.getLExpression());
      if (!(assignee instanceof PsiReferenceExpression)) return;
      rRef = (PsiReferenceExpression)assignee;
    } else {
      rRef = (PsiReferenceExpression)rExpression;
    }
    PsiReferenceExpression lRef = (PsiReferenceExpression)lExpression;
    PsiManager manager = assignment.getManager();
    if (!sameInstanceReferences(lRef, rRef, manager)) return;
    final PsiVariable variable = (PsiVariable)lRef.resolve();
    if (variable == null) return;
    holder.registerProblem(assignment, InspectionsBundle.message("assignment.to.itself.problem.descriptor", variable.getName()),
                           ProblemHighlightType.LIKE_UNUSED_SYMBOL);
  }

  /**
   * @return true if both expressions resolve to the same variable/class or field in the same instance of the class
   */
  private static boolean sameInstanceReferences(@Nullable PsiJavaCodeReferenceElement lRef, @Nullable PsiJavaCodeReferenceElement rRef, PsiManager manager) {
    if (lRef == null && rRef == null) return true;
    if (lRef == null || rRef == null) return false;
    PsiElement lResolved = lRef.resolve();
    PsiElement rResolved = rRef.resolve();
    if (!manager.areElementsEquivalent(lResolved, rResolved)) return false;
    if (!(lResolved instanceof PsiVariable)) return false;
    final PsiVariable variable = (PsiVariable)lResolved;
    if (variable.hasModifierProperty(PsiModifier.STATIC)) return true;

    final PsiElement lQualifier = lRef.getQualifier();
    final PsiElement rQualifier = rRef.getQualifier();
    if (lQualifier instanceof PsiJavaCodeReferenceElement && rQualifier instanceof PsiJavaCodeReferenceElement) {
      return sameInstanceReferences((PsiJavaCodeReferenceElement)lQualifier, (PsiJavaCodeReferenceElement)rQualifier, manager);
    }

    if (Comparing.equal(lQualifier, rQualifier)) return true;
    boolean lThis = lQualifier == null || lQualifier instanceof PsiThisExpression || lQualifier instanceof PsiSuperExpression;
    boolean rThis = rQualifier == null || rQualifier instanceof PsiThisExpression || rQualifier instanceof PsiSuperExpression;
    if (lThis && rThis) {
      final PsiJavaCodeReferenceElement llQualifier = getQualifier(lQualifier);
      final PsiJavaCodeReferenceElement rrQualifier = getQualifier(rQualifier);
      return sameInstanceReferences(llQualifier, rrQualifier, manager);
    }
    return false;
  }

  private static PsiJavaCodeReferenceElement getQualifier(PsiElement qualifier) {
    if (qualifier instanceof PsiThisExpression) {
      final PsiJavaCodeReferenceElement thisQualifier = ((PsiThisExpression)qualifier).getQualifier();
      if (thisQualifier != null) {
        final PsiClass innerMostClass = PsiTreeUtil.getParentOfType(thisQualifier, PsiClass.class);
        if (innerMostClass == thisQualifier.resolve()) {
          return null;
        }
      }
      return thisQualifier;
    } else if (qualifier != null) {
      return  ((PsiSuperExpression)qualifier).getQualifier();
    }
    return null;
  }
}
