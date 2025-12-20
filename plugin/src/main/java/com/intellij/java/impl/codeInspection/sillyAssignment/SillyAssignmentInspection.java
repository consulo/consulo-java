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
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author anna
 * @since 2005-11-15
 */
@ExtensionImpl
public class SillyAssignmentInspection extends BaseJavaLocalInspectionTool {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionVariableAssignedToItselfDisplayName();
    }

    @Override
    @Nonnull
    public String getShortName() {
        return "SillyAssignment";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    public PsiElementVisitor buildVisitorImpl(
        @Nonnull final ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        Object state
    ) {
        return new JavaElementVisitor() {

            @Override
            public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression expression) {
                checkSillyAssignment(expression, holder);
            }

            @Override
            public void visitReferenceExpression(@Nonnull PsiReferenceExpression expression) {
                visitElement(expression);
            }

            @Override
            @RequiredReadAction
            public void visitVariable(@Nonnull PsiVariable variable) {
                PsiExpression initializer = PsiUtil.deparenthesizeExpression(variable.getInitializer());
                if (initializer instanceof PsiAssignmentExpression assignmentExpression) {
                    PsiExpression lExpr = PsiUtil.deparenthesizeExpression(assignmentExpression.getLExpression());
                    checkExpression(variable, lExpr);
                }
                else {
                    checkExpression(variable, initializer);
                }
            }

            @RequiredReadAction
            private void checkExpression(PsiVariable variable, PsiExpression expression) {
                if (!(expression instanceof PsiReferenceExpression)) {
                    return;
                }
                PsiReferenceExpression refExpr = (PsiReferenceExpression) expression;
                PsiExpression qualifier = refExpr.getQualifierExpression();
                if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression
                    || variable.hasModifierProperty(PsiModifier.STATIC)) {
                    if (refExpr.isReferenceTo(variable)) {
                        holder.registerProblem(
                            expression,
                            InspectionLocalize.assignmentToDeclaredVariableProblemDescriptor(variable.getName()).get(),
                            ProblemHighlightType.LIKE_UNUSED_SYMBOL
                        );
                    }
                }
            }
        };
    }

    private static void checkSillyAssignment(PsiAssignmentExpression assignment, ProblemsHolder holder) {
        if (assignment.getOperationTokenType() != JavaTokenType.EQ) {
            return;
        }
        PsiExpression lExpression = assignment.getLExpression();
        PsiExpression rExpression = assignment.getRExpression();
        if (rExpression == null) {
            return;
        }
        lExpression = PsiUtil.deparenthesizeExpression(lExpression);
        rExpression = PsiUtil.deparenthesizeExpression(rExpression);
        if (!(lExpression instanceof PsiReferenceExpression)) {
            return;
        }
        PsiReferenceExpression rRef;
        if (rExpression instanceof PsiReferenceExpression rReferenceExpression) {
            rRef = rReferenceExpression;
        }
        else if (rExpression instanceof PsiAssignmentExpression rAssignmentExpression) {
            PsiExpression assignee = PsiUtil.deparenthesizeExpression(rAssignmentExpression.getLExpression());
            if (assignee instanceof PsiReferenceExpression referenceExpressionAssignee) {
                rRef = referenceExpressionAssignee;
            }
            else {
                return;
            }
        }
        else {
            return;
        }
        PsiReferenceExpression lRef = (PsiReferenceExpression) lExpression;
        PsiManager manager = assignment.getManager();
        if (!sameInstanceReferences(lRef, rRef, manager)) {
            return;
        }
        PsiVariable variable = (PsiVariable) lRef.resolve();
        if (variable == null) {
            return;
        }
        holder.registerProblem(
            assignment,
            InspectionLocalize.assignmentToItselfProblemDescriptor(variable.getName()).get(),
            ProblemHighlightType.LIKE_UNUSED_SYMBOL
        );
    }

    /**
     * @return true if both expressions resolve to the same variable/class or field in the same instance of the class
     */
    @RequiredReadAction
    private static boolean sameInstanceReferences(
        @Nullable PsiJavaCodeReferenceElement lRef,
        @Nullable PsiJavaCodeReferenceElement rRef,
        PsiManager manager
    ) {
        if (lRef == null && rRef == null) {
            return true;
        }
        if (lRef == null || rRef == null) {
            return false;
        }
        PsiElement lResolved = lRef.resolve();
        PsiElement rResolved = rRef.resolve();
        if (!manager.areElementsEquivalent(lResolved, rResolved)) {
            return false;
        }
        if (!(lResolved instanceof PsiVariable)) {
            return false;
        }
        PsiVariable variable = (PsiVariable) lResolved;
        if (variable.hasModifierProperty(PsiModifier.STATIC)) {
            return true;
        }

        PsiElement lQualifier = lRef.getQualifier();
        PsiElement rQualifier = rRef.getQualifier();
        if (lQualifier instanceof PsiJavaCodeReferenceElement lJavaRef && rQualifier instanceof PsiJavaCodeReferenceElement rJavaRef) {
            return sameInstanceReferences(lJavaRef, rJavaRef, manager);
        }

        if (Comparing.equal(lQualifier, rQualifier)) {
            return true;
        }
        boolean lThis = lQualifier == null || lQualifier instanceof PsiThisExpression || lQualifier instanceof PsiSuperExpression;
        boolean rThis = rQualifier == null || rQualifier instanceof PsiThisExpression || rQualifier instanceof PsiSuperExpression;
        if (lThis && rThis) {
            PsiJavaCodeReferenceElement llQualifier = getQualifier(lQualifier);
            PsiJavaCodeReferenceElement rrQualifier = getQualifier(rQualifier);
            return sameInstanceReferences(llQualifier, rrQualifier, manager);
        }
        return false;
    }

    @RequiredReadAction
    private static PsiJavaCodeReferenceElement getQualifier(PsiElement qualifier) {
        if (qualifier instanceof PsiThisExpression thisExpression) {
            PsiJavaCodeReferenceElement thisQualifier = thisExpression.getQualifier();
            if (thisQualifier != null) {
                PsiClass innerMostClass = PsiTreeUtil.getParentOfType(thisQualifier, PsiClass.class);
                if (innerMostClass == thisQualifier.resolve()) {
                    return null;
                }
            }
            return thisQualifier;
        }
        else if (qualifier != null) {
            return ((PsiSuperExpression) qualifier).getQualifier();
        }
        return null;
    }
}
