/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.migration;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

@ExtensionImpl
public class UnnecessaryUnboxingInspection extends BaseInspection {

    @SuppressWarnings("PublicField")
    public boolean onlyReportSuperfluouslyUnboxed = false;

    @NonNls
    static final Map<String, String> s_unboxingMethods = new HashMap<>(8);

    static {
        s_unboxingMethods.put(CommonClassNames.JAVA_LANG_INTEGER, "intValue");
        s_unboxingMethods.put(CommonClassNames.JAVA_LANG_SHORT, "shortValue");
        s_unboxingMethods.put(CommonClassNames.JAVA_LANG_BOOLEAN, "booleanValue");
        s_unboxingMethods.put(CommonClassNames.JAVA_LANG_LONG, "longValue");
        s_unboxingMethods.put(CommonClassNames.JAVA_LANG_BYTE, "byteValue");
        s_unboxingMethods.put(CommonClassNames.JAVA_LANG_FLOAT, "floatValue");
        s_unboxingMethods.put(CommonClassNames.JAVA_LANG_DOUBLE, "doubleValue");
        s_unboxingMethods.put(CommonClassNames.JAVA_LANG_CHARACTER, "charValue");
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unnecessaryUnboxingDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.unnecessaryUnboxingProblemDescriptor().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nullable
    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.unnecessaryUnboxingSuperfluousOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "onlyReportSuperfluouslyUnboxed");
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryUnboxingFix();
    }

    private static class UnnecessaryUnboxingFix extends InspectionGadgetsFix {
        @Override
        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.unnecessaryUnboxingRemoveQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) {
            PsiMethodCallExpression methodCall = (PsiMethodCallExpression) descriptor.getPsiElement();
            PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            PsiExpression strippedQualifier = ParenthesesUtils.stripParentheses(qualifier);
            if (strippedQualifier == null) {
                return;
            }
            if (strippedQualifier instanceof PsiReferenceExpression) {
                PsiReferenceExpression referenceExpression = (PsiReferenceExpression) strippedQualifier;
                PsiElement element = referenceExpression.resolve();
                if (element instanceof PsiField) {
                    PsiField field = (PsiField) element;
                    PsiClass containingClass = field.getContainingClass();
                    if (containingClass == null) {
                        return;
                    }
                    String classname = containingClass.getQualifiedName();
                    if (CommonClassNames.JAVA_LANG_BOOLEAN.equals(classname)) {
                        @NonNls String name = field.getName();
                        if ("TRUE".equals(name)) {
                            PsiReplacementUtil.replaceExpression(methodCall, "true");
                            return;
                        }
                        else if ("FALSE".equals(name)) {
                            PsiReplacementUtil.replaceExpression(methodCall, "false");
                            return;
                        }
                    }
                }
            }
            String strippedQualifierText = strippedQualifier.getText();
            PsiReplacementUtil.replaceExpression(methodCall, strippedQualifierText);
        }
    }

    @Override
    public boolean shouldInspect(PsiFile file) {
        return PsiUtil.isLanguageLevel5OrHigher(file);
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryUnboxingVisitor();
    }

    private class UnnecessaryUnboxingVisitor extends BaseInspectionVisitor {

        @Override
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!isUnboxingExpression(expression)) {
                return;
            }
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null || !canRemainBoxed(expression, qualifier)) {
                return;
            }
            registerError(expression);
        }

        private boolean canRemainBoxed(@Nonnull PsiExpression expression, @Nonnull PsiExpression unboxedExpression) {
            PsiElement parent = expression.getParent();
            while (parent instanceof PsiParenthesizedExpression) {
                expression = (PsiExpression) parent;
                parent = parent.getParent();
            }
            if (parent instanceof PsiPolyadicExpression) {
                PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) parent;
                if (isPossibleObjectComparison(expression, polyadicExpression)) {
                    return false;
                }
            }
            if (parent instanceof PsiTypeCastExpression) {
                PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression) parent;
                PsiTypeElement typeElement = typeCastExpression.getCastType();
                if (typeElement == null) {
                    return false;
                }
                PsiType castType = typeElement.getType();
                PsiType expressionType = expression.getType();
                if (expressionType == null || !castType.isAssignableFrom(expressionType)) {
                    return false;
                }
            }
            else if (parent instanceof PsiConditionalExpression) {
                PsiConditionalExpression conditionalExpression = (PsiConditionalExpression) parent;
                PsiExpression thenExpression = conditionalExpression.getThenExpression();
                if (thenExpression == null) {
                    return false;
                }
                PsiExpression elseExpression = conditionalExpression.getElseExpression();
                if (elseExpression == null) {
                    return false;
                }
                if (PsiTreeUtil.isAncestor(thenExpression, expression, false)) {
                    PsiType type = elseExpression.getType();
                    if (!(type instanceof PsiPrimitiveType)) {
                        return false;
                    }
                }
                else if (PsiTreeUtil.isAncestor(elseExpression, expression, false)) {
                    PsiType type = thenExpression.getType();
                    if (!(type instanceof PsiPrimitiveType)) {
                        return false;
                    }
                }
            }
            else if (parent instanceof PsiExpressionList) {
                PsiElement grandParent = parent.getParent();
                if (!(grandParent instanceof PsiCallExpression)) {
                    return true;
                }
                PsiCallExpression methodCallExpression = (PsiCallExpression) grandParent;
                if (!isSameMethodCalledWithoutUnboxing(methodCallExpression, expression, unboxedExpression)) {
                    return false;
                }
            }
            if (onlyReportSuperfluouslyUnboxed) {
                PsiType expectedType = ExpectedTypeUtils.findExpectedType(expression, false, true);
                if (!(expectedType instanceof PsiClassType)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isPossibleObjectComparison(PsiExpression expression, PsiPolyadicExpression polyadicExpression) {
            if (!ComparisonUtils.isEqualityComparison(polyadicExpression)) {
                return false;
            }
            for (PsiExpression operand : polyadicExpression.getOperands()) {
                if (operand == expression) {
                    continue;
                }
                if (!(operand.getType() instanceof PsiPrimitiveType) || isUnboxingExpression(operand)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isUnboxingExpression(PsiExpression expression) {
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return false;
            }
            PsiType qualifierType = qualifier.getType();
            if (qualifierType == null) {
                return false;
            }
            String qualifierTypeName = qualifierType.getCanonicalText();
            if (!s_unboxingMethods.containsKey(qualifierTypeName)) {
                return false;
            }
            String methodName = methodExpression.getReferenceName();
            String unboxingMethod = s_unboxingMethods.get(qualifierTypeName);
            return unboxingMethod.equals(methodName);
        }

        private boolean isSameMethodCalledWithoutUnboxing(@Nonnull PsiCallExpression callExpression, @Nonnull PsiExpression unboxingExpression, @Nonnull PsiExpression unboxedExpression) {
            PsiMethod originalMethod = callExpression.resolveMethod();
            if (originalMethod == null) {
                return false;
            }
            PsiMethod method = MethodCallUtils.findMethodWithReplacedArgument(callExpression, unboxingExpression, unboxedExpression);
            return originalMethod == method;
        }
    }
}