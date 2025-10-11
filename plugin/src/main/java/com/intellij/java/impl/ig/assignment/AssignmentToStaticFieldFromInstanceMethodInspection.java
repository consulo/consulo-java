/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.assignment;

import com.intellij.java.impl.ig.psiutils.WellFormednessUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class AssignmentToStaticFieldFromInstanceMethodInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.assignmentToStaticFieldFromInstanceMethodDisplayName();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.assignmentToStaticFieldFromInstanceMethodProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AssignmentToStaticFieldFromInstanceMethod();
    }

    private static class AssignmentToStaticFieldFromInstanceMethod
        extends BaseInspectionVisitor {

        @Override
        public void visitAssignmentExpression(
            @Nonnull PsiAssignmentExpression expression
        ) {
            if (!WellFormednessUtils.isWellFormed(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            checkForStaticFieldAccess(lhs);
        }

        @Override
        public void visitPrefixExpression(
            @Nonnull PsiPrefixExpression expression
        ) {
            final IElementType tokenType = expression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            checkForStaticFieldAccess(operand);
        }

        @Override
        public void visitPostfixExpression(
            @Nonnull PsiPostfixExpression expression
        ) {
            final IElementType tokenType = expression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            checkForStaticFieldAccess(operand);
        }

        private void checkForStaticFieldAccess(PsiExpression expression) {
            if (!(expression instanceof PsiReferenceExpression)) {
                return;
            }
            if (isInStaticMethod(expression)) {
                return;
            }
            final PsiElement referent = ((PsiReference) expression).resolve();
            if (referent == null) {
                return;
            }
            if (!(referent instanceof PsiField)) {
                return;
            }
            final PsiField fieldReferenced = (PsiField) referent;
            if (fieldReferenced.hasModifierProperty(PsiModifier.STATIC)) {
                registerError(expression);
            }
        }

        private static boolean isInStaticMethod(PsiElement element) {
            final PsiMember member =
                PsiTreeUtil.getParentOfType(element,
                    PsiMethod.class, PsiClassInitializer.class
                );
            if (member == null) {
                return false;
            }
            return member.hasModifierProperty(PsiModifier.STATIC);
        }
    }
}
