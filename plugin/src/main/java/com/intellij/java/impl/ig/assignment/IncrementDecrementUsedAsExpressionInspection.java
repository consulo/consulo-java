/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.scope.SearchScope;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class IncrementDecrementUsedAsExpressionInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern("[a-zA-Z_0-9.]+")
    public String getID() {
        return "ValueOfIncrementOrDecrementUsed";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.incrementDecrementDisplayName();
    }

    @Nonnull
    @Override
    public String buildErrorString(Object... infos) {
        Object info = infos[0];
        if (info instanceof PsiPostfixExpression postfixExpression) {
            IElementType tokenType = postfixExpression.getOperationTokenType();
            return JavaTokenType.PLUSPLUS.equals(tokenType)
                ? InspectionGadgetsLocalize.valueOfPostIncrementProblemDescriptor().get()
                : InspectionGadgetsLocalize.valueOfPostDecrementProblemDescriptor().get();
        }
        else {
            PsiPrefixExpression prefixExpression = (PsiPrefixExpression) info;
            IElementType tokenType = prefixExpression.getOperationTokenType();
            return JavaTokenType.PLUSPLUS.equals(tokenType)
                ? InspectionGadgetsLocalize.valueOfPreIncrementProblemDescriptor().get()
                : InspectionGadgetsLocalize.valueOfPreDecrementProblemDescriptor().get();
        }
    }

    @Nullable
    @Override
    @RequiredReadAction
    protected InspectionGadgetsFix buildFix(Object... infos) {
        PsiExpression expression = (PsiExpression) infos[0];
        return new IncrementDecrementUsedAsExpressionFix(expression.getText());
    }

    private static class IncrementDecrementUsedAsExpressionFix extends InspectionGadgetsFix {
        private final String elementText;

        IncrementDecrementUsedAsExpressionFix(String elementText) {
            this.elementText = elementText;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.incrementDecrementUsedAsExpressionQuickfix(elementText);
        }

        @Override
        @RequiredWriteAction
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            // see also the Extract Increment intention of IPP
            PsiElement element = descriptor.getPsiElement();
            PsiExpression operand;
            if (element instanceof PsiPostfixExpression postfixExpression) {
                operand = postfixExpression.getOperand();
            }
            else {
                PsiPrefixExpression prefixExpression = (PsiPrefixExpression) element;
                operand = prefixExpression.getOperand();
            }
            if (operand == null) {
                return;
            }
            PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
            if (statement == null) {
                return;
            }
            PsiElement parent = statement.getParent();
            if (parent == null) {
                return;
            }
            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            String newStatementText = element.getText() + ';';
            String operandText = operand.getText();
            if (parent instanceof PsiIfStatement || parent instanceof PsiLoopStatement) {
                // need to add braces because
                // in/decrement is inside braceless control statement body
                StringBuilder text = new StringBuilder();
                text.append('{');
                String elementText = getElementText(statement, element, operandText);
                if (element instanceof PsiPostfixExpression) {
                    text.append(elementText).append(newStatementText);
                }
                else {
                    text.append(newStatementText).append(elementText);
                }
                text.append('}');
                PsiCodeBlock codeBlock = factory.createCodeBlockFromText(text.toString(), parent);
                statement.replace(codeBlock);
                return;
            }
            PsiStatement newStatement = factory.createStatementFromText(newStatementText, element);
            if (statement instanceof PsiReturnStatement returnStatement) {
                if (element instanceof PsiPostfixExpression) {
                    // special handling of postfix expression in return statement
                    PsiExpression returnValue = returnStatement.getReturnValue();
                    if (returnValue == null) {
                        return;
                    }
                    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
                    String variableName = javaCodeStyleManager.suggestUniqueVariableName("result", returnValue, true);
                    PsiType type = returnValue.getType();
                    if (type == null) {
                        return;
                    }
                    String newReturnValueText = getElementText(returnValue, element, operandText);
                    String declarationStatementText = type.getCanonicalText() + ' ' + variableName + '=' + newReturnValueText + ';';
                    PsiStatement declarationStatement = factory.createStatementFromText(declarationStatementText, returnStatement);
                    parent.addBefore(declarationStatement, statement);
                    parent.addBefore(newStatement, statement);
                    PsiStatement newReturnStatement = factory.createStatementFromText("return " + variableName + ';', returnStatement);
                    returnStatement.replace(newReturnStatement);
                    return;
                }
                else {
                    parent.addBefore(newStatement, statement);
                }
            }
            else if (statement instanceof PsiThrowStatement throwStatement) {
                if (element instanceof PsiPostfixExpression) {
                    // special handling of postfix expression in throw statement
                    PsiExpression exception = throwStatement.getException();
                    if (exception == null) {
                        return;
                    }
                    JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
                    String variableName = javaCodeStyleManager.suggestUniqueVariableName("e", exception, true);
                    PsiType type = exception.getType();
                    if (type == null) {
                        return;
                    }
                    String newReturnValueText = getElementText(exception, element, operandText);
                    String declarationStatementText = type.getCanonicalText() + ' ' + variableName + '=' + newReturnValueText + ';';
                    PsiStatement declarationStatement = factory.createStatementFromText(declarationStatementText, throwStatement);
                    parent.addBefore(declarationStatement, statement);
                    parent.addBefore(newStatement, statement);
                    PsiStatement newReturnStatement = factory.createStatementFromText("throw " + variableName + ';', throwStatement);
                    throwStatement.replace(newReturnStatement);
                    return;
                }
                else {
                    parent.addBefore(newStatement, statement);
                }
            }
            else if (!(statement instanceof PsiForStatement)) {
                if (element instanceof PsiPostfixExpression) {
                    parent.addAfter(newStatement, statement);
                }
                else {
                    parent.addBefore(newStatement, statement);
                }
            }
            else if (operand instanceof PsiReferenceExpression referenceExpression) {
                PsiElement target = referenceExpression.resolve();
                if (target != null) {
                    SearchScope useScope = target.getUseScope();
                    if (!new LocalSearchScope(statement).equals(useScope)) {
                        if (element instanceof PsiPostfixExpression) {
                            parent.addAfter(newStatement, statement);
                        }
                        else {
                            parent.addBefore(newStatement, statement);
                        }
                    }
                }
            }
            if (statement instanceof PsiLoopStatement loopStatement) {
                // in/decrement inside loop statement condition
                PsiStatement body = loopStatement.getBody();
                if (body instanceof PsiBlockStatement blockStatement) {
                    PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                    if (element instanceof PsiPostfixExpression) {
                        PsiElement firstElement = codeBlock.getFirstBodyElement();
                        codeBlock.addBefore(newStatement, firstElement);
                    }
                    else {
                        codeBlock.add(newStatement);
                    }
                }
                else {
                    StringBuilder blockText = new StringBuilder();
                    blockText.append('{');
                    if (element instanceof PsiPostfixExpression) {
                        blockText.append(newStatementText);
                        if (body != null) {
                            blockText.append(body.getText());
                        }
                    }
                    else {
                        if (body != null) {
                            blockText.append(body.getText());
                        }
                        blockText.append(newStatementText);
                    }
                    blockText.append('}');
                    PsiStatement blockStatement = factory.createStatementFromText(blockText.toString(), statement);
                    if (body == null) {
                        loopStatement.add(blockStatement);
                    }
                    else {
                        body.replace(blockStatement);
                    }
                }
            }
            replaceExpression((PsiExpression) element, operandText);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new IncrementDecrementUsedAsExpressionVisitor();
    }

    private static class IncrementDecrementUsedAsExpressionVisitor extends BaseInspectionVisitor {
        @Override
        public void visitPostfixExpression(@Nonnull PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            PsiElement parent = expression.getParent();
            if (parent instanceof PsiExpressionStatement
                || (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiExpressionListStatement)) {
                return;
            }
            IElementType tokenType = expression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) && !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            registerError(expression, expression);
        }

        @Override
        public void visitPrefixExpression(@Nonnull PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            PsiElement parent = expression.getParent();
            if (parent instanceof PsiExpressionStatement
                || (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiExpressionListStatement)) {
                return;
            }
            IElementType tokenType = expression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            registerError(expression, expression);
        }
    }
}