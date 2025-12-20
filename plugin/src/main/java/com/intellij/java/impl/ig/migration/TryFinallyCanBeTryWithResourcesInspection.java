/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author Bas Leijdekkers
 */
@ExtensionImpl
public class TryFinallyCanBeTryWithResourcesInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.tryFinallyCanBeTryWithResourcesDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.tryFinallyCanBeTryWithResourcesProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new TryFinallyCanBeTryWithResourcesFix();
    }

    private static class TryFinallyCanBeTryWithResourcesFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.tryFinallyCanBeTryWithResourcesQuickfix();
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = element.getParent();
            if (!(parent instanceof PsiTryStatement)) {
                return;
            }
            PsiTryStatement tryStatement = (PsiTryStatement) parent;
            PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
            if (finallyBlock == null) {
                return;
            }
            PsiElement[] tryBlockChildren = tryBlock.getChildren();
            Set<PsiLocalVariable> variables = new HashSet();
            for (PsiLocalVariable variable : collectVariables(tryStatement)) {
                if (!isVariableUsedOutsideContext(variable, tryBlock)) {
                    variables.add(variable);
                }
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            @NonNls StringBuilder newTryStatementText = new StringBuilder("try (");
            Set<Integer> unwantedChildren = new HashSet(2);
            boolean separator = false;
            for (PsiLocalVariable variable : variables) {
                boolean hasInitializer;
                PsiExpression initializer = variable.getInitializer();
                if (initializer == null) {
                    hasInitializer = false;
                }
                else {
                    PsiType type = initializer.getType();
                    hasInitializer = !PsiType.NULL.equals(type);
                }
                if (separator) {
                    newTryStatementText.append(';');
                }
                newTryStatementText.append(variable.getTypeElement().getText()).append(' ').append(variable.getName()).append('=');
                if (hasInitializer) {
                    newTryStatementText.append(initializer.getText());
                }
                else {
                    int index = findInitialization(tryBlockChildren, variable, hasInitializer);
                    if (index < 0) {
                        return;
                    }
                    unwantedChildren.add(Integer.valueOf(index));
                    PsiExpressionStatement expressionStatement = (PsiExpressionStatement) tryBlockChildren[index];
                    if (expressionStatement.getNextSibling() instanceof PsiWhiteSpace) {
                        unwantedChildren.add(Integer.valueOf(index + 1));
                    }
                    PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) expressionStatement.getExpression();
                    PsiExpression rhs = assignmentExpression.getRExpression();
                    if (rhs == null) {
                        return;
                    }
                    newTryStatementText.append(rhs.getText());
                }
                separator = true;
            }
            newTryStatementText.append(") {");
            int tryBlockStatementsLength = tryBlockChildren.length - 1;
            for (int i = 1; i < tryBlockStatementsLength; i++) {
                PsiElement child = tryBlockChildren[i];
                if (unwantedChildren.contains(Integer.valueOf(i))) {
                    continue;
                }
                newTryStatementText.append(child.getText());
            }
            newTryStatementText.append('}');
            PsiCatchSection[] catchSections = tryStatement.getCatchSections();
            for (PsiCatchSection catchSection : catchSections) {
                newTryStatementText.append(catchSection.getText());
            }
            PsiElement[] finallyChildren = finallyBlock.getChildren();
            boolean appended = false;
            int finallyChildrenLength = finallyChildren.length - 1;
            List<PsiElement> savedComments = new ArrayList();
            for (int i = 1; i < finallyChildrenLength; i++) {
                PsiElement child = finallyChildren[i];
                if (isCloseStatement(child, variables)) {
                    continue;
                }
                if (!appended) {
                    if (child instanceof PsiComment) {
                        PsiElement prevSibling = child.getPrevSibling();
                        if (prevSibling instanceof PsiWhiteSpace) {
                            savedComments.add(prevSibling);
                        }
                        savedComments.add(child);
                    }
                    else if (!(child instanceof PsiWhiteSpace)) {
                        newTryStatementText.append(" finally {");
                        for (PsiElement savedComment : savedComments) {
                            newTryStatementText.append(savedComment.getText());
                        }
                        newTryStatementText.append(child.getText());
                        appended = true;
                    }
                }
                else {
                    newTryStatementText.append(child.getText());
                }
            }
            if (appended) {
                newTryStatementText.append('}');
            }
            for (PsiLocalVariable variable : variables) {
                variable.delete();
            }
            if (!appended) {
                int savedCommentsSize = savedComments.size();
                PsiElement parent1 = tryStatement.getParent();
                for (int i = savedCommentsSize - 1; i >= 0; i--) {
                    PsiElement savedComment = savedComments.get(i);
                    parent1.addAfter(savedComment, tryStatement);
                }
            }
            PsiStatement newTryStatement = factory.createStatementFromText(newTryStatementText.toString(), element);
            tryStatement.replace(newTryStatement);
        }

        private static boolean isCloseStatement(PsiElement element, Set<PsiLocalVariable> variables) {
            if (element instanceof PsiExpressionStatement) {
                PsiExpressionStatement expressionStatement = (PsiExpressionStatement) element;
                PsiExpression expression = expressionStatement.getExpression();
                if (!(expression instanceof PsiMethodCallExpression)) {
                    return false;
                }
                PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
                PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
                String methodName = methodExpression.getReferenceName();
                if (!HardcodedMethodConstants.CLOSE.equals(methodName)) {
                    return false;
                }
                PsiExpression qualifier = methodExpression.getQualifierExpression();
                if (!(qualifier instanceof PsiReferenceExpression)) {
                    return false;
                }
                PsiReferenceExpression referenceExpression = (PsiReferenceExpression) qualifier;
                PsiElement target = referenceExpression.resolve();
                if (!(target instanceof PsiLocalVariable)) {
                    return false;
                }
                PsiLocalVariable variable = (PsiLocalVariable) target;
                return variables.contains(variable);
            }
            else if (element instanceof PsiIfStatement) {
                PsiIfStatement ifStatement = (PsiIfStatement) element;
                if (ifStatement.getElseBranch() != null) {
                    return false;
                }
                PsiExpression condition = ifStatement.getCondition();
                if (!(condition instanceof PsiBinaryExpression)) {
                    return false;
                }
                PsiBinaryExpression binaryExpression = (PsiBinaryExpression) condition;
                IElementType tokenType = binaryExpression.getOperationTokenType();
                if (!JavaTokenType.NE.equals(tokenType)) {
                    return false;
                }
                PsiExpression lhs = binaryExpression.getLOperand();
                PsiExpression rhs = binaryExpression.getROperand();
                if (rhs == null) {
                    return false;
                }
                PsiElement variable;
                if (PsiType.NULL.equals(rhs.getType())) {
                    if (!(lhs instanceof PsiReferenceExpression)) {
                        return false;
                    }
                    PsiReferenceExpression referenceExpression = (PsiReferenceExpression) lhs;
                    variable = referenceExpression.resolve();
                    if (!(variable instanceof PsiLocalVariable)) {
                        return false;
                    }
                }
                else if (PsiType.NULL.equals(lhs.getType())) {
                    if (!(rhs instanceof PsiReferenceExpression)) {
                        return false;
                    }
                    PsiReferenceExpression referenceExpression = (PsiReferenceExpression) rhs;
                    variable = referenceExpression.resolve();
                    if (!(variable instanceof PsiLocalVariable)) {
                        return false;
                    }
                }
                else {
                    return false;
                }
                PsiStatement thenBranch = ifStatement.getThenBranch();
                if (thenBranch instanceof PsiExpressionStatement) {
                    return isCloseStatement(thenBranch, variables);
                }
                else if (thenBranch instanceof PsiBlockStatement) {
                    PsiBlockStatement blockStatement = (PsiBlockStatement) thenBranch;
                    PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                    PsiStatement[] statements = codeBlock.getStatements();
                    return statements.length == 1 && isCloseStatement(statements[0], variables);
                }
                else {
                    return false;
                }
            }
            else {
                return false;
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new TryFinallyCanBeTryWithResourcesVisitor();
    }

    private static class TryFinallyCanBeTryWithResourcesVisitor extends BaseInspectionVisitor {
        @Override
        public void visitTryStatement(PsiTryStatement tryStatement) {
            super.visitTryStatement(tryStatement);
            if (!PsiUtil.isLanguageLevel7OrHigher(tryStatement)) {
                return;
            }
            PsiResourceList resourceList = tryStatement.getResourceList();
            if (resourceList != null) {
                return;
            }
            PsiCodeBlock tryBlock = tryStatement.getTryBlock();
            if (tryBlock == null) {
                return;
            }
            List<PsiLocalVariable> variables = collectVariables(tryStatement);
            if (variables.isEmpty()) {
                return;
            }
            PsiStatement[] tryBlockStatements = tryBlock.getStatements();
            boolean found = false;
            for (PsiVariable variable : variables) {
                boolean hasInitializer;
                PsiExpression initializer = variable.getInitializer();
                if (initializer == null) {
                    hasInitializer = false;
                }
                else {
                    PsiType type = initializer.getType();
                    hasInitializer = !PsiType.NULL.equals(type);
                }
                int index = findInitialization(tryBlockStatements, variable, hasInitializer);
                if (index >= 0 ^ hasInitializer) {
                    if (isVariableUsedOutsideContext(variable, tryBlock)) {
                        continue;
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                return;
            }
            registerStatementError(tryStatement);
        }
    }

    static boolean isVariableUsedOutsideContext(PsiVariable variable, PsiElement context) {
        VariableUsedOutsideContextVisitor visitor = new VariableUsedOutsideContextVisitor(variable, context);
        PsiElement declarationScope = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
        if (declarationScope == null) {
            return true;
        }
        declarationScope.accept(visitor);
        return visitor.variableIsUsed();
    }

    static List<PsiLocalVariable> collectVariables(PsiTryStatement tryStatement) {
        PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock == null) {
            return Collections.EMPTY_LIST;
        }
        PsiStatement[] statements = finallyBlock.getStatements();
        if (statements.length == 0) {
            return Collections.EMPTY_LIST;
        }
        List<PsiLocalVariable> variables = new ArrayList();
        for (PsiStatement statement : statements) {
            PsiLocalVariable variable = findAutoCloseableVariable(statement);
            if (variable != null) {
                variables.add(variable);
            }
        }
        return variables;
    }

    @Nullable
    static PsiLocalVariable findAutoCloseableVariable(PsiStatement statement) {
        if (statement instanceof PsiIfStatement) {
            PsiIfStatement ifStatement = (PsiIfStatement) statement;
            if (ifStatement.getElseBranch() != null) {
                return null;
            }
            PsiExpression condition = ifStatement.getCondition();
            if (!(condition instanceof PsiBinaryExpression)) {
                return null;
            }
            PsiBinaryExpression binaryExpression = (PsiBinaryExpression) condition;
            IElementType tokenType = binaryExpression.getOperationTokenType();
            if (!JavaTokenType.NE.equals(tokenType)) {
                return null;
            }
            PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            if (rhs == null) {
                return null;
            }
            PsiElement variable;
            if (PsiType.NULL.equals(rhs.getType())) {
                if (!(lhs instanceof PsiReferenceExpression)) {
                    return null;
                }
                PsiReferenceExpression referenceExpression = (PsiReferenceExpression) lhs;
                variable = referenceExpression.resolve();
                if (!(variable instanceof PsiLocalVariable)) {
                    return null;
                }
            }
            else if (PsiType.NULL.equals(lhs.getType())) {
                if (!(rhs instanceof PsiReferenceExpression)) {
                    return null;
                }
                PsiReferenceExpression referenceExpression = (PsiReferenceExpression) rhs;
                variable = referenceExpression.resolve();
                if (!(variable instanceof PsiLocalVariable)) {
                    return null;
                }
            }
            else {
                return null;
            }
            PsiStatement thenBranch = ifStatement.getThenBranch();
            PsiLocalVariable resourceVariable;
            if (thenBranch instanceof PsiExpressionStatement) {
                resourceVariable = findAutoCloseableVariable(thenBranch);
            }
            else if (thenBranch instanceof PsiBlockStatement) {
                PsiBlockStatement blockStatement = (PsiBlockStatement) thenBranch;
                PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length != 1) {
                    return null;
                }
                resourceVariable = findAutoCloseableVariable(statements[0]);
            }
            else {
                return null;
            }
            if (variable.equals(resourceVariable)) {
                return resourceVariable;
            }
        }
        else if (statement instanceof PsiExpressionStatement) {
            PsiExpressionStatement expressionStatement = (PsiExpressionStatement) statement;
            PsiExpression expression = expressionStatement.getExpression();
            if (!(expression instanceof PsiMethodCallExpression)) {
                return null;
            }
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.CLOSE.equals(methodName)) {
                return null;
            }
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return null;
            }
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) qualifier;
            PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiLocalVariable) || target instanceof PsiResourceVariable) {
                return null;
            }
            PsiLocalVariable variable = (PsiLocalVariable) target;
            if (!isAutoCloseable(variable)) {
                return null;
            }
            return variable;
        }
        return null;
    }

    private static boolean isAutoCloseable(PsiVariable variable) {
        PsiType type = variable.getType();
        if (!(type instanceof PsiClassType)) {
            return false;
        }
        PsiClassType classType = (PsiClassType) type;
        PsiClass aClass = classType.resolve();
        return aClass != null && InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE);
    }

    static int findInitialization(
        PsiElement[] elements, PsiVariable variable,
        boolean hasInitializer
    ) {
        int result = -1;
        int statementsLength = elements.length;
        for (int i = 0; i < statementsLength; i++) {
            PsiElement element = elements[i];
            if (!(element instanceof PsiExpressionStatement)) {
                continue;
            }
            PsiExpressionStatement expressionStatement = (PsiExpressionStatement) element;
            PsiExpression expression = expressionStatement.getExpression();
            if (!(expression instanceof PsiAssignmentExpression)) {
                continue;
            }
            PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) expression;
            PsiExpression lhs = assignmentExpression.getLExpression();
            if (!(lhs instanceof PsiReferenceExpression)) {
                continue;
            }
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) lhs;
            PsiElement target = referenceExpression.resolve();
            if (variable.equals(target)) {
                if (result >= 0 && !hasInitializer) {
                    return -1;
                }
                result = i;
            }
        }
        return result;
    }

    static class VariableUsedOutsideContextVisitor extends JavaRecursiveElementVisitor {

        private boolean used = false;
        @Nonnull
        private final PsiVariable variable;
        private final PsiElement skipContext;

        public VariableUsedOutsideContextVisitor(@Nonnull PsiVariable variable, PsiElement skipContext) {
            this.variable = variable;
            this.skipContext = skipContext;
        }

        @Override
        public void visitElement(@Nonnull PsiElement element) {
            if (element.equals(skipContext)) {
                return;
            }
            if (used) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitReferenceExpression(@Nonnull PsiReferenceExpression referenceExpression) {
            if (used) {
                return;
            }
            super.visitReferenceExpression(referenceExpression);
            PsiElement target = referenceExpression.resolve();
            if (target == null) {
                return;
            }
            if (target.equals(variable) && !isCloseMethodCalled(referenceExpression)) {
                used = true;
            }
        }

        private static boolean isCloseMethodCalled(PsiReferenceExpression referenceExpression) {
            PsiMethodCallExpression methodCallExpression =
                PsiTreeUtil.getParentOfType(referenceExpression, PsiMethodCallExpression.class);
            if (methodCallExpression == null) {
                return false;
            }
            PsiExpressionList argumentList = methodCallExpression.getArgumentList();
            if (argumentList.getExpressions().length != 0) {
                return false;
            }
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            String name = methodExpression.getReferenceName();
            return HardcodedMethodConstants.CLOSE.equals(name);
        }

        public boolean variableIsUsed() {
            return used;
        }
    }
}
