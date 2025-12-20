/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class StringBufferReplaceableByStringInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.stringBufferReplaceableByStringDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        PsiElement element = (PsiElement) infos[0];
        if (element instanceof PsiNewExpression) {
            return InspectionGadgetsLocalize.newStringBufferReplaceableByStringProblemDescriptor().get();
        }
        String typeText = ((PsiType) infos[1]).getPresentableText();
        return InspectionGadgetsLocalize.stringBufferReplaceableByStringProblemDescriptor(typeText).get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        String typeText = ((PsiType) infos[1]).getCanonicalText();
        return new StringBufferReplaceableByStringFix(CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(typeText));
    }

    private static class StringBufferReplaceableByStringFix extends InspectionGadgetsFix {
        private final boolean isStringBuilder;

        private StringBufferReplaceableByStringFix(boolean isStringBuilder) {
            this.isStringBuilder = isStringBuilder;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return isStringBuilder
                ? InspectionGadgetsLocalize.stringBuilderReplaceableByStringQuickfix()
                : InspectionGadgetsLocalize.stringBufferReplaceableByStringQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiVariable)) {
                if (parent instanceof PsiNewExpression) {
                    PsiNewExpression newExpression = (PsiNewExpression) parent;
                    PsiExpression stringBuilderExpression = getCompleteExpression(newExpression);
                    StringBuilder stringExpression = buildStringExpression(stringBuilderExpression, new StringBuilder());
                    if (stringExpression != null && stringBuilderExpression != null) {
                        replaceExpression(stringBuilderExpression, stringExpression.toString());
                    }
                }
                return;
            }
            PsiVariable variable = (PsiVariable) parent;
            PsiTypeElement originalTypeElement = variable.getTypeElement();
            if (originalTypeElement == null) {
                return;
            }
            PsiExpression initializer = variable.getInitializer();
            if (initializer == null) {
                return;
            }
            StringBuilder stringExpression = buildStringExpression(initializer, new StringBuilder());
            if (stringExpression == null) {
                return;
            }
            PsiClassType javaLangString = TypeUtils.getStringType(variable);
            PsiTypeElement typeElement = JavaPsiFacade.getElementFactory(project).createTypeElement(javaLangString);
            replaceExpression(initializer, stringExpression.toString());
            originalTypeElement.replace(typeElement);
        }

        @Nullable
        private static StringBuilder buildStringExpression(PsiExpression expression, StringBuilder result) {
            if (expression instanceof PsiNewExpression) {
                PsiNewExpression newExpression = (PsiNewExpression) expression;
                PsiExpressionList argumentList = newExpression.getArgumentList();
                if (argumentList == null) {
                    return null;
                }
                PsiExpression[] arguments = argumentList.getExpressions();
                if (arguments.length == 1) {
                    PsiExpression argument = arguments[0];
                    PsiType type = argument.getType();
                    if (!PsiType.INT.equals(type)) {
                        result.append(argument.getText());
                        if (type != null && type.equalsToText(CommonClassNames.JAVA_LANG_CHAR_SEQUENCE)) {
                            result.append(".toString()");
                        }
                    }
                }
                PsiElement parent = expression.getParent();
                if (result.length() == 0 && parent instanceof PsiVariable) {
                    result.append("\"\"");
                }
                return result;
            }
            else if (expression instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
                PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
                PsiExpression qualifier = methodExpression.getQualifierExpression();
                result = buildStringExpression(qualifier, result);
                if (result == null) {
                    return null;
                }
                if ("toString".equals(methodExpression.getReferenceName())) {
                    if (result.length() == 0) {
                        result.append("\"\"");
                    }
                }
                else {
                    PsiExpressionList argumentList = methodCallExpression.getArgumentList();
                    PsiExpression[] arguments = argumentList.getExpressions();
                    if (arguments.length != 1) {
                        return null;
                    }
                    PsiExpression argument = arguments[0];
                    PsiType type = argument.getType();
                    String argumentText = argument.getText();
                    if (result.length() != 0) {
                        result.append('+');
                        if (ParenthesesUtils.getPrecedence(argument) > ParenthesesUtils.ADDITIVE_PRECEDENCE ||
                            (type instanceof PsiPrimitiveType && ParenthesesUtils.getPrecedence(argument) == ParenthesesUtils.ADDITIVE_PRECEDENCE)) {
                            result.append('(').append(argumentText).append(')');
                        }
                        else {
                            if (StringUtil.startsWithChar(argumentText, '+')) {
                                result.append(' ');
                            }
                            result.append(argumentText);
                        }
                    }
                    else {
                        if (type instanceof PsiPrimitiveType) {
                            if (argument instanceof PsiLiteralExpression) {
                                PsiLiteralExpression literalExpression = (PsiLiteralExpression) argument;
                                result.append('"').append(literalExpression.getValue()).append('"');
                            }
                            else {
                                result.append("String.valueOf(").append(argumentText).append(")");
                            }
                        }
                        else {
                            if (ParenthesesUtils.getPrecedence(argument) >= ParenthesesUtils.ADDITIVE_PRECEDENCE) {
                                result.append('(').append(argumentText).append(')');
                            }
                            else {
                                if (type != null && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                                    result.append("String.valueOf(").append(argumentText).append(")");
                                }
                                else {
                                    result.append(argumentText);
                                }
                            }
                        }
                    }
                }
                return result;
            }
            return null;
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new StringBufferReplaceableByStringVisitor();
    }

    private static class StringBufferReplaceableByStringVisitor extends BaseInspectionVisitor {
        @Override
        public void visitLocalVariable(@Nonnull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (codeBlock == null) {
                return;
            }
            PsiType type = variable.getType();
            if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUFFER, type) &&
                !TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUILDER, type)) {
                return;
            }
            PsiExpression initializer = variable.getInitializer();
            if (initializer == null) {
                return;
            }
            if (!isNewStringBufferOrStringBuilder(initializer)) {
                return;
            }
            if (VariableAccessUtils.variableIsAssigned(variable, codeBlock)) {
                return;
            }
            if (VariableAccessUtils.variableIsAssignedFrom(variable, codeBlock)) {
                return;
            }
            if (VariableAccessUtils.variableIsReturned(variable, codeBlock)) {
                return;
            }
            if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, codeBlock)) {
                return;
            }
            if (variableIsModified(variable, codeBlock)) {
                return;
            }
            registerVariableError(variable, variable, type);
        }

        @Override
        public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            PsiType type = expression.getType();
            if (!TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUFFER, type) &&
                !TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_STRING_BUILDER, type)) {
                return;
            }
            PsiExpression completeExpression = getCompleteExpression(expression);
            if (completeExpression == null) {
                return;
            }
            registerNewExpressionError(expression, expression, type);
        }

        public static boolean variableIsModified(PsiVariable variable, PsiElement context) {
            VariableIsModifiedVisitor visitor = new VariableIsModifiedVisitor(variable);
            context.accept(visitor);
            return visitor.isModified();
        }

        private static boolean isNewStringBufferOrStringBuilder(PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            else if (expression instanceof PsiNewExpression) {
                return true;
            }
            else if (expression instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
                if (!isAppend(methodCallExpression)) {
                    return false;
                }
                PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
                PsiExpression qualifier = methodExpression.getQualifierExpression();
                return isNewStringBufferOrStringBuilder(qualifier);
            }
            return false;
        }

        public static boolean isAppend(PsiMethodCallExpression methodCallExpression) {
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            return "append".equals(methodName);
        }
    }

    @Nullable
    private static PsiExpression getCompleteExpression(PsiNewExpression expression) {
        PsiElement completeExpression = expression;
        boolean found = false;
        while (true) {
            PsiElement parent = completeExpression.getParent();
            if (!(parent instanceof PsiReferenceExpression)) {
                break;
            }
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) parent;
            PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
                break;
            }
            String name = referenceExpression.getReferenceName();
            if ("append".equals(name)) {
                PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) grandParent;
                PsiExpressionList argumentList = methodCallExpression.getArgumentList();
                PsiExpression[] arguments = argumentList.getExpressions();
                if (arguments.length != 1) {
                    return null;
                }
            }
            else {
                if (!"toString".equals(name)) {
                    return null;
                }
                found = true;
            }
            completeExpression = grandParent;
            if (found) {
                return (PsiExpression) completeExpression;
            }
        }
        return null;
    }
}
