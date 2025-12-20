/*
 * Copyright 2011-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class MismatchedStringBuilderQueryUpdateInspection extends BaseInspection {
    private static final Set<String> returnSelfNames = new HashSet();

    static {
        returnSelfNames.add("append");
        returnSelfNames.add("appendCodePoint");
        returnSelfNames.add("delete");
        returnSelfNames.add("deleteCharAt");
        returnSelfNames.add("insert");
        returnSelfNames.add("replace");
        returnSelfNames.add("reverse");
    }

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "MismatchedQueryAndUpdateOfStringBuilder";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.mismatchedStringBuilderQueryUpdateDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        boolean updated = (Boolean) infos[0];
        PsiType type = (PsiType) infos[1]; //"StringBuilder";
        return updated
            ? InspectionGadgetsLocalize.mismatchedStringBuilderUpdatedProblemDescriptor(type.getPresentableText()).get()
            : InspectionGadgetsLocalize.mismatchedStringBuilderQueriedProblemDescriptor(type.getPresentableText()).get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public boolean runForWholeFile() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new MismatchedQueryAndUpdateOfStringBuilderVisitor();
    }

    private static class MismatchedQueryAndUpdateOfStringBuilderVisitor extends BaseInspectionVisitor {

        @Override
        public void visitField(PsiField field) {
            super.visitField(field);
            if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            PsiClass containingClass = PsiUtil.getTopLevelClass(field);
            if (!checkVariable(field, containingClass)) {
                return;
            }
            boolean queried = stringBuilderContentsAreQueried(field, containingClass);
            boolean updated = stringBuilderContentsAreUpdated(field, containingClass);
            if (queried == updated) {
                return;
            }
            registerFieldError(field, Boolean.valueOf(updated), field.getType());
        }

        @Override
        public void visitLocalVariable(PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (!checkVariable(variable, codeBlock)) {
                return;
            }
            boolean queried = stringBuilderContentsAreQueried(variable, codeBlock);
            boolean updated = stringBuilderContentsAreUpdated(variable, codeBlock);
            if (queried == updated) {
                return;
            }
            registerVariableError(variable, Boolean.valueOf(updated), variable.getType());
        }

        private static boolean checkVariable(PsiVariable variable, PsiElement context) {
            if (context == null) {
                return false;
            }
            if (!TypeUtils.variableHasTypeOrSubtype(variable, CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER)) {
                return false;
            }
            if (VariableAccessUtils.variableIsAssigned(variable, context)) {
                return false;
            }
            if (VariableAccessUtils.variableIsAssignedFrom(variable, context)) {
                return false;
            }
            if (VariableAccessUtils.variableIsReturned(variable, context)) {
                return false;
            }
            if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, context)) {
                return false;
            }
            return !VariableAccessUtils.variableIsUsedInArrayInitializer(variable, context);
        }

        private static boolean stringBuilderContentsAreUpdated(
            PsiVariable variable, PsiElement context
        ) {
            PsiExpression initializer = variable.getInitializer();
            if (initializer != null && !isDefaultConstructorCall(initializer)) {
                return true;
            }
            return isStringBuilderUpdated(variable, context);
        }

        private static boolean stringBuilderContentsAreQueried(PsiVariable variable, PsiElement context) {
            return isStringBuilderQueried(variable, context);
        }

        private static boolean isDefaultConstructorCall(PsiExpression initializer) {
            if (!(initializer instanceof PsiNewExpression)) {
                return false;
            }
            PsiNewExpression newExpression = (PsiNewExpression) initializer;
            PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
            if (classReference == null) {
                return false;
            }
            PsiElement target = classReference.resolve();
            if (!(target instanceof PsiClass)) {
                return false;
            }
            PsiClass aClass = (PsiClass) target;
            String qualifiedName = aClass.getQualifiedName();
            if (!CommonClassNames.JAVA_LANG_STRING_BUILDER.equals(qualifiedName) &&
                !CommonClassNames.JAVA_LANG_STRING_BUFFER.equals(qualifiedName)) {
                return false;
            }
            PsiExpressionList argumentList = newExpression.getArgumentList();
            if (argumentList == null) {
                return false;
            }
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length == 0) {
                return true;
            }
            PsiExpression argument = arguments[0];
            PsiType argumentType = argument.getType();
            return PsiType.INT.equals(argumentType);
        }
    }

    public static boolean isStringBuilderUpdated(PsiVariable variable, PsiElement context) {
        StringBuilderUpdateCalledVisitor visitor = new StringBuilderUpdateCalledVisitor(variable);
        context.accept(visitor);
        return visitor.isUpdated();
    }

    private static class StringBuilderUpdateCalledVisitor extends JavaRecursiveElementVisitor {

        @NonNls
        private static final Set<String> updateNames = new HashSet();

        static {
            updateNames.add("append");
            updateNames.add("appendCodePoint");
            updateNames.add("delete");
            updateNames.add("delete");
            updateNames.add("deleteCharAt");
            updateNames.add("insert");
            updateNames.add("replace");
            updateNames.add("setCharAt");
        }

        private final PsiVariable variable;
        boolean updated = false;

        public StringBuilderUpdateCalledVisitor(PsiVariable variable) {
            this.variable = variable;
        }

        public boolean isUpdated() {
            return updated;
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (updated) {
                return;
            }
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String name = methodExpression.getReferenceName();
            if (!updateNames.contains(name)) {
                return;
            }
            PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
            if (hasReferenceToVariable(variable, qualifierExpression)) {
                updated = true;
            }
        }
    }

    public static boolean isStringBuilderQueried(PsiVariable variable, PsiElement context) {
        StringBuilderQueryCalledVisitor visitor = new StringBuilderQueryCalledVisitor(variable);
        context.accept(visitor);
        return visitor.isQueried();
    }

    private static class StringBuilderQueryCalledVisitor extends JavaRecursiveElementVisitor {

        @NonNls
        private static final Set<String> queryNames = new HashSet();

        static {
            queryNames.add("toString");
            queryNames.add("indexOf");
            queryNames.add("lastIndexOf");
            queryNames.add("capacity");
            queryNames.add("charAt");
            queryNames.add("codePointAt");
            queryNames.add("codePointBefore");
            queryNames.add("codePointCount");
            queryNames.add("equals");
            queryNames.add("getChars");
            queryNames.add("hashCode");
            queryNames.add("length");
            queryNames.add("offsetByCodePoints");
            queryNames.add("subSequence");
            queryNames.add("substring");
        }

        private final PsiVariable variable;
        private boolean queried = false;

        private StringBuilderQueryCalledVisitor(PsiVariable variable) {
            this.variable = variable;
        }

        public boolean isQueried() {
            return queried;
        }

        @Override
        public void visitElement(@Nonnull PsiElement element) {
            if (queried) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitReferenceExpression(PsiReferenceExpression expression) {
            if (queried) {
                return;
            }
            super.visitReferenceExpression(expression);
            PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
            if (!(parent instanceof PsiPolyadicExpression)) {
                return;
            }
            PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) parent;
            IElementType tokenType = polyadicExpression.getOperationTokenType();
            if (!JavaTokenType.PLUS.equals(tokenType)) {
                return;
            }
            PsiElement target = expression.resolve();
            if (!variable.equals(target)) {
                return;
            }
            PsiType type = polyadicExpression.getType();
            if (type == null || !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                return;
            }
            queried = true;
        }

        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            if (queried) {
                return;
            }
            super.visitMethodCallExpression(expression);
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String name = methodExpression.getReferenceName();
            PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
            if (!queryNames.contains(name)) {
                if (returnSelfNames.contains(name) && hasReferenceToVariable(variable, qualifierExpression) && isVariableValueUsed(
                    expression)) {
                    queried = true;
                }
                return;
            }
            if (hasReferenceToVariable(variable, qualifierExpression)) {
                queried = true;
            }
        }
    }

    private static boolean isVariableValueUsed(PsiExpression expression) {
        PsiElement parent = expression.getParent();
        if (parent instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) parent;
            return isVariableValueUsed(parenthesizedExpression);
        }
        else if (parent instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression) parent;
            return isVariableValueUsed(typeCastExpression);
        }
        else if (parent instanceof PsiReturnStatement) {
            return true;
        }
        else if (parent instanceof PsiExpressionList) {
            PsiElement grandParent = parent.getParent();
            if (grandParent instanceof PsiMethodCallExpression) {
                return true;
            }
        }
        else if (parent instanceof PsiArrayInitializerExpression) {
            return true;
        }
        else if (parent instanceof PsiAssignmentExpression) {
            PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) parent;
            PsiExpression rhs = assignmentExpression.getRExpression();
            return expression.equals(rhs);
        }
        else if (parent instanceof PsiVariable) {
            PsiVariable variable = (PsiVariable) parent;
            PsiExpression initializer = variable.getInitializer();
            return expression.equals(initializer);
        }
        return false;
    }

    private static boolean hasReferenceToVariable(PsiVariable variable, PsiElement element) {
        if (element instanceof PsiReferenceExpression) {
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) element;
            PsiElement target = referenceExpression.resolve();
            if (variable.equals(target)) {
                return true;
            }
        }
        else if (element instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) element;
            PsiExpression expression = parenthesizedExpression.getExpression();
            return hasReferenceToVariable(variable, expression);
        }
        else if (element instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) element;
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            String name = methodExpression.getReferenceName();
            if (returnSelfNames.contains(name)) {
                return hasReferenceToVariable(variable, methodExpression.getQualifierExpression());
            }
        }
        else if (element instanceof PsiConditionalExpression) {
            PsiConditionalExpression conditionalExpression = (PsiConditionalExpression) element;
            PsiExpression thenExpression = conditionalExpression.getThenExpression();
            if (hasReferenceToVariable(variable, thenExpression)) {
                return true;
            }
            PsiExpression elseExpression = conditionalExpression.getElseExpression();
            return hasReferenceToVariable(variable, elseExpression);
        }
        return false;
    }
}
