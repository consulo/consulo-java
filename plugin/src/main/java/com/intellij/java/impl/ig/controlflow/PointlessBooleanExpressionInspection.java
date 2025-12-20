/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.controlflow;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.ConstantExpressionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ExtensionImpl
public class PointlessBooleanExpressionInspection extends BaseInspection {
    private static final Set<IElementType> booleanTokens = new HashSet<IElementType>();

    static {
        booleanTokens.add(JavaTokenType.ANDAND);
        booleanTokens.add(JavaTokenType.AND);
        booleanTokens.add(JavaTokenType.OROR);
        booleanTokens.add(JavaTokenType.OR);
        booleanTokens.add(JavaTokenType.XOR);
        booleanTokens.add(JavaTokenType.EQEQ);
        booleanTokens.add(JavaTokenType.NE);
    }

    @SuppressWarnings("PublicField")
    public boolean m_ignoreExpressionsContainingConstants = false;

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.pointlessBooleanExpressionIgnoreOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "m_ignoreExpressionsContainingConstants");
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.pointlessBooleanExpressionDisplayName();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        PsiExpression expression = (PsiExpression) infos[0];
        return InspectionGadgetsLocalize.booleanExpressionCanBeSimplifiedProblemDescriptor(
            buildSimplifiedExpression(expression, new StringBuilder())
        ).get();
    }

    private StringBuilder buildSimplifiedExpression(@Nullable PsiExpression expression, StringBuilder out) {
        if (expression instanceof PsiPolyadicExpression) {
            buildSimplifiedPolyadicExpression((PsiPolyadicExpression) expression, out);
        }
        else if (expression instanceof PsiPrefixExpression) {
            buildSimplifiedPrefixExpression((PsiPrefixExpression) expression, out);
        }
        else if (expression instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) expression;
            PsiExpression expression1 = parenthesizedExpression.getExpression();
            out.append('(');
            buildSimplifiedExpression(expression1, out);
            out.append(')');
        }
        else if (expression != null) {
            out.append(expression.getText());
        }
        return out;
    }

    private void buildSimplifiedPolyadicExpression(PsiPolyadicExpression expression, StringBuilder out) {
        IElementType tokenType = expression.getOperationTokenType();
        PsiExpression[] operands = expression.getOperands();
        List<PsiExpression> expressions = new ArrayList();
        if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.AND)) {
            for (PsiExpression operand : operands) {
                if (evaluate(operand) == Boolean.TRUE) {
                    continue;
                }
                else if (evaluate(operand) == Boolean.FALSE) {
                    out.append(PsiKeyword.FALSE);
                    return;
                }
                expressions.add(operand);
            }
            if (expressions.isEmpty()) {
                out.append(PsiKeyword.TRUE);
                return;
            }
            buildSimplifiedExpression(expressions, tokenType.equals(JavaTokenType.ANDAND) ? "&&" : "&", false, out);
        }
        else if (tokenType.equals(JavaTokenType.OROR) || tokenType.equals(JavaTokenType.OR)) {
            for (PsiExpression operand : operands) {
                if (evaluate(operand) == Boolean.FALSE) {
                    continue;
                }
                else if (evaluate(operand) == Boolean.TRUE) {
                    out.append(PsiKeyword.TRUE);
                    return;
                }
                expressions.add(operand);
            }
            if (expressions.isEmpty()) {
                out.append(PsiKeyword.FALSE);
                return;
            }
            buildSimplifiedExpression(expressions, tokenType.equals(JavaTokenType.OROR) ? "||" : "|", false, out);
        }
        else if (tokenType.equals(JavaTokenType.XOR) || tokenType.equals(JavaTokenType.NE)) {
            boolean negate = false;
            for (PsiExpression operand : operands) {
                if (evaluate(operand) == Boolean.FALSE) {
                    continue;
                }
                else if (evaluate(operand) == Boolean.TRUE) {
                    negate = !negate;
                    continue;
                }
                expressions.add(operand);
            }
            if (expressions.isEmpty()) {
                if (negate) {
                    out.append(PsiKeyword.TRUE);
                }
                else {
                    out.append(PsiKeyword.FALSE);
                }
                return;
            }
            buildSimplifiedExpression(expressions, tokenType.equals(JavaTokenType.XOR) ? "^" : "!=", negate, out);
        }
        else if (tokenType.equals(JavaTokenType.EQEQ)) {
            boolean negate = false;
            for (PsiExpression operand : operands) {
                if (evaluate(operand) == Boolean.TRUE) {
                    continue;
                }
                else if (evaluate(operand) == Boolean.FALSE) {
                    negate = !negate;
                    continue;
                }
                expressions.add(operand);
            }
            if (expressions.isEmpty()) {
                if (negate) {
                    out.append(PsiKeyword.FALSE);
                }
                else {
                    out.append(PsiKeyword.TRUE);
                }
                return;
            }
            buildSimplifiedExpression(expressions, "==", negate, out);
        }
        else {
            out.append(expression.getText());
        }
    }

    private void buildSimplifiedExpression(List<PsiExpression> expressions, String token, boolean negate, StringBuilder out) {
        if (expressions.size() == 1) {
            PsiExpression expression = expressions.get(0);
            if (isBoxedTypeComparison(token, expression)) {
                out.append(expression.getText()).append(" != null && ");
            }
            if (!negate) {
                out.append(expression.getText());
                return;
            }
            if (ComparisonUtils.isComparison(expression)) {
                PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
                String negatedComparison = ComparisonUtils.getNegatedComparison(binaryExpression.getOperationTokenType());
                PsiExpression lhs = binaryExpression.getLOperand();
                PsiExpression rhs = binaryExpression.getROperand();
                assert rhs != null;
                out.append(lhs.getText()).append(negatedComparison).append(rhs.getText());
            }
            else {
                if (ParenthesesUtils.getPrecedence(expression) > ParenthesesUtils.PREFIX_PRECEDENCE) {
                    out.append("!(").append(expression.getText()).append(')');
                }
                else {
                    out.append('!').append(expression.getText());
                }
            }
        }
        else {
            if (negate) {
                out.append("!(");
            }
            boolean useToken = false;
            for (PsiExpression expression : expressions) {
                if (useToken) {
                    out.append(token);
                    PsiElement previousSibling = expression.getPrevSibling();
                    if (previousSibling instanceof PsiWhiteSpace) {
                        out.append(previousSibling.getText());
                    }
                }
                else {
                    useToken = true;
                }
                buildSimplifiedExpression(expression, out);
                PsiElement nextSibling = expression.getNextSibling();
                if (nextSibling instanceof PsiWhiteSpace) {
                    out.append(nextSibling.getText());
                }
            }
            if (negate) {
                out.append(')');
            }
        }
    }

    private static boolean isBoxedTypeComparison(String token, PsiExpression expression) {
        return ("==".equals(token) || "!=".equals(token)) && expression instanceof PsiReferenceExpression && expression.getType() instanceof PsiClassType;
    }

    private void buildSimplifiedPrefixExpression(PsiPrefixExpression expression, StringBuilder out) {
        PsiJavaToken sign = expression.getOperationSign();
        IElementType tokenType = sign.getTokenType();
        PsiExpression operand = expression.getOperand();
        if (JavaTokenType.EXCL.equals(tokenType)) {
            Boolean value = evaluate(operand);
            if (value == Boolean.TRUE) {
                out.append(PsiKeyword.FALSE);
                return;
            }
            else if (value == Boolean.FALSE) {
                out.append(PsiKeyword.TRUE);
                return;
            }
        }
        buildSimplifiedExpression(operand, out.append(sign.getText()));
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new PointlessBooleanExpressionFix();
    }

    private class PointlessBooleanExpressionFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.constantConditionalExpressionSimplifyQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiExpression)) {
                return;
            }
            PsiExpression expression = (PsiExpression) element;
            replaceExpression(expression, buildSimplifiedExpression(expression, new StringBuilder()).toString());
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new PointlessBooleanExpressionVisitor();
    }

    private class PointlessBooleanExpressionVisitor extends BaseInspectionVisitor {

        @Override
        public void visitPolyadicExpression(PsiPolyadicExpression expression) {
            super.visitPolyadicExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitPrefixExpression(PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            checkExpression(expression);
        }

        private void checkExpression(PsiExpression expression) {
            if (!isPointlessBooleanExpression(expression)) {
                return;
            }
            PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
            if (parent instanceof PsiExpression && isPointlessBooleanExpression((PsiExpression) parent)) {
                return;
            }
            registerError(expression, expression);
        }

        private boolean isPointlessBooleanExpression(PsiExpression expression) {
            if (expression instanceof PsiPrefixExpression) {
                return evaluate(expression) != null;
            }
            else if (expression instanceof PsiPolyadicExpression) {
                PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) expression;
                IElementType sign = polyadicExpression.getOperationTokenType();
                if (!booleanTokens.contains(sign)) {
                    return false;
                }
                PsiExpression[] operands = polyadicExpression.getOperands();
                boolean containsConstant = false;
                for (PsiExpression operand : operands) {
                    if (operand == null) {
                        return false;
                    }
                    PsiType type = operand.getType();
                    if (type == null || !type.equals(PsiType.BOOLEAN) && !type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
                        return false;
                    }
                    containsConstant |= (evaluate(operand) != null);
                }
                if (!containsConstant) {
                    return false;
                }
                return true;
            }
            return false;
        }
    }

    @Nullable
    private Boolean evaluate(@Nullable PsiExpression expression) {
        if (expression == null || m_ignoreExpressionsContainingConstants && containsReference(expression)) {
            return null;
        }
        if (expression instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) expression;
            return evaluate(parenthesizedExpression.getExpression());
        }
        else if (expression instanceof PsiPolyadicExpression) {
            PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) expression;
            IElementType tokenType = polyadicExpression.getOperationTokenType();
            if (tokenType.equals(JavaTokenType.OROR)) {
                PsiExpression[] operands = polyadicExpression.getOperands();
                for (PsiExpression operand : operands) {
                    if (evaluate(operand) == Boolean.TRUE) {
                        return Boolean.TRUE;
                    }
                }
            }
            else if (tokenType.equals(JavaTokenType.ANDAND)) {
                PsiExpression[] operands = polyadicExpression.getOperands();
                for (PsiExpression operand : operands) {
                    if (evaluate(operand) == Boolean.FALSE) {
                        return Boolean.FALSE;
                    }
                }
            }
        }
        else if (expression instanceof PsiPrefixExpression) {
            PsiPrefixExpression prefixExpression = (PsiPrefixExpression) expression;
            IElementType tokenType = prefixExpression.getOperationTokenType();
            if (JavaTokenType.EXCL.equals(tokenType)) {
                PsiExpression operand = prefixExpression.getOperand();
                Boolean b = evaluate(operand);
                if (b == Boolean.FALSE) {
                    return Boolean.TRUE;
                }
                else if (b == Boolean.TRUE) {
                    return Boolean.FALSE;
                }
            }
        }
        Boolean value = (Boolean) ConstantExpressionUtil.computeCastTo(expression, PsiType.BOOLEAN);
        return value != null ? value.booleanValue() : null;
    }

    private static boolean containsReference(@Nullable PsiExpression expression) {
        if (expression == null) {
            return false;
        }
        ReferenceVisitor visitor = new ReferenceVisitor();
        expression.accept(visitor);
        return visitor.containsReference();
    }

    private static class ReferenceVisitor extends JavaRecursiveElementVisitor {

        private boolean referenceFound = false;

        @Override
        public void visitElement(PsiElement element) {
            if (referenceFound) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            PsiElement target = expression.resolve();
            if (target instanceof PsiField && ExpressionUtils.isConstant((PsiField) target)) {
                referenceFound = true;
            }
            else {
                super.visitReferenceExpression(expression);
            }
        }

        public boolean containsReference() {
            return referenceFound;
        }
    }
}