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
package com.intellij.java.impl.ipp.increment;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.scope.SearchScope;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ExtractIncrementIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class ExtractIncrementIntention extends MutablyNamedIntention {

    @Nonnull
    @Override
    public LocalizeValue getTextForElement(PsiElement element) {
        PsiJavaToken sign;
        if (element instanceof PsiPostfixExpression) {
            sign = ((PsiPostfixExpression) element).getOperationSign();
        }
        else {
            sign = ((PsiPrefixExpression) element).getOperationSign();
        }
        String operator = sign.getText();
        return IntentionPowerPackLocalize.extractIncrementIntentionName(operator);
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.extractIncrementIntentionFamilyName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ExtractIncrementPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        PsiExpression operand;
        if (element instanceof PsiPostfixExpression) {
            PsiPostfixExpression postfixExpression =
                (PsiPostfixExpression) element;
            operand = postfixExpression.getOperand();
        }
        else {
            PsiPrefixExpression prefixExpression =
                (PsiPrefixExpression) element;
            operand = prefixExpression.getOperand();
        }
        if (operand == null) {
            return;
        }
        PsiStatement statement =
            PsiTreeUtil.getParentOfType(element, PsiStatement.class);
        if (statement == null) {
            return;
        }
        PsiElement parent = statement.getParent();
        if (parent == null) {
            return;
        }
        Project project = element.getProject();
        PsiElementFactory factory =
            JavaPsiFacade.getInstance(project).getElementFactory();
        String newStatementText = element.getText() + ';';
        String operandText = operand.getText();
        if (parent instanceof PsiIfStatement ||
            parent instanceof PsiLoopStatement) {
            // need to add braces because
            // in/decrement is inside braceless control statement body
            StringBuilder text = new StringBuilder();
            text.append('{');
            String elementText =
                getElementText(statement, element, operandText);
            if (element instanceof PsiPostfixExpression) {
                text.append(elementText);
                text.append(newStatementText);
            }
            else {
                text.append(newStatementText);
                text.append(elementText);
            }
            text.append('}');
            PsiCodeBlock codeBlock =
                factory.createCodeBlockFromText(text.toString(), parent);
            statement.replace(codeBlock);
            return;
        }
        PsiStatement newStatement =
            factory.createStatementFromText(newStatementText, element);
        if (statement instanceof PsiReturnStatement) {
            if (element instanceof PsiPostfixExpression) {
                // special handling of postfix expression in return statement
                PsiReturnStatement returnStatement =
                    (PsiReturnStatement) statement;
                PsiExpression returnValue =
                    returnStatement.getReturnValue();
                if (returnValue == null) {
                    return;
                }
                JavaCodeStyleManager javaCodeStyleManager =
                    JavaCodeStyleManager.getInstance(project);
                String variableName =
                    javaCodeStyleManager.suggestUniqueVariableName(
                        "result", returnValue, true);
                PsiType type = returnValue.getType();
                if (type == null) {
                    return;
                }
                String newReturnValueText = getElementText(
                    returnValue, element, operandText);
                String declarationStatementText =
                    type.getCanonicalText() + ' ' + variableName +
                        '=' + newReturnValueText + ';';
                PsiStatement declarationStatement =
                    factory.createStatementFromText(declarationStatementText,
                        returnStatement);
                parent.addBefore(declarationStatement, statement);
                parent.addBefore(newStatement, statement);
                PsiStatement newReturnStatement =
                    factory.createStatementFromText(
                        "return " + variableName + ';',
                        returnStatement);
                returnStatement.replace(newReturnStatement);
                return;
            }
            else {
                parent.addBefore(newStatement, statement);
            }
        }
        else if (statement instanceof PsiThrowStatement) {
            if (element instanceof PsiPostfixExpression) {
                // special handling of postfix expression in throw statement
                PsiThrowStatement returnStatement =
                    (PsiThrowStatement) statement;
                PsiExpression exception =
                    returnStatement.getException();
                if (exception == null) {
                    return;
                }
                JavaCodeStyleManager javaCodeStyleManager =
                    JavaCodeStyleManager.getInstance(project);
                String variableName =
                    javaCodeStyleManager.suggestUniqueVariableName(
                        "e", exception, true);
                PsiType type = exception.getType();
                if (type == null) {
                    return;
                }
                String newReturnValueText = getElementText(
                    exception, element, operandText);
                String declarationStatementText =
                    type.getCanonicalText() + ' ' + variableName +
                        '=' + newReturnValueText + ';';
                PsiStatement declarationStatement =
                    factory.createStatementFromText(declarationStatementText,
                        returnStatement);
                parent.addBefore(declarationStatement, statement);
                parent.addBefore(newStatement, statement);
                PsiStatement newReturnStatement =
                    factory.createStatementFromText(
                        "throw " + variableName + ';',
                        returnStatement);
                returnStatement.replace(newReturnStatement);
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
        else if (operand instanceof PsiReferenceExpression) {
            PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) operand;
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
        if (statement instanceof PsiLoopStatement) {
            // in/decrement inside loop statement condition
            PsiLoopStatement loopStatement = (PsiLoopStatement) statement;
            PsiStatement body = loopStatement.getBody();
            if (body instanceof PsiBlockStatement) {
                PsiBlockStatement blockStatement =
                    (PsiBlockStatement) body;
                PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                if (element instanceof PsiPostfixExpression) {
                    PsiElement firstElement =
                        codeBlock.getFirstBodyElement();
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
                PsiStatement blockStatement =
                    factory.createStatementFromText(blockText.toString(),
                        statement);
                if (body == null) {
                    loopStatement.add(blockStatement);
                }
                else {
                    body.replace(blockStatement);
                }
            }
        }
        replaceExpression(operandText, (PsiExpression) element);
    }

    private static String getElementText(@Nonnull PsiElement element,
                                         @Nullable PsiElement elementToReplace,
                                         @Nullable String replacement) {
        StringBuilder out = new StringBuilder();
        getElementText(element, elementToReplace, replacement, out);
        return out.toString();
    }

    private static void getElementText(
        @Nonnull PsiElement element,
        @Nullable PsiElement elementToReplace,
        @Nullable String replacement,
        @Nonnull StringBuilder out) {
        if (element.equals(elementToReplace)) {
            out.append(replacement);
            return;
        }
        PsiElement[] children = element.getChildren();
        if (children.length == 0) {
            out.append(element.getText());
            return;
        }
        for (PsiElement child : children) {
            getElementText(child, elementToReplace, replacement, out);
        }
    }
}