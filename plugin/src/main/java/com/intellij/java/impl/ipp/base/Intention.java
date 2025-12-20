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
package com.intellij.java.impl.ipp.base;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import consulo.codeEditor.Editor;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.BaseElementAtCaretIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.virtualFileSystem.ReadonlyStatusHandler;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

public abstract class Intention extends BaseElementAtCaretIntentionAction {

    private final PsiElementPredicate predicate;

    protected Intention() {
        predicate = getElementPredicate();
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
        if (!isWritable(project, element)) {
            return;
        }
        PsiElement matchingElement = findMatchingElement(element, editor);
        if (matchingElement == null) {
            return;
        }
        processIntention(editor, matchingElement);
    }

    protected abstract void processIntention(@Nonnull PsiElement element);

    protected void processIntention(Editor editor, @Nonnull PsiElement element) {
        processIntention(element);
    }

    @Nonnull
    protected abstract PsiElementPredicate getElementPredicate();

    protected static void replaceExpression(@Nonnull String newExpression, @Nonnull PsiExpression expression) {
        Project project = expression.getProject();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiExpression newCall = factory.createExpressionFromText(newExpression, expression);
        PsiElement insertedElement = expression.replace(newCall);
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceExpressionWithNegatedExpression(@Nonnull PsiExpression newExpression, @Nonnull PsiExpression expression) {
        Project project = expression.getProject();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiExpression expressionToReplace = expression;
        String newExpressionText = newExpression.getText();
        String expString;
        if (BoolUtils.isNegated(expression)) {
            expressionToReplace = BoolUtils.findNegation(expression);
            expString = newExpressionText;
        }
        else if (ComparisonUtils.isComparison(newExpression)) {
            PsiBinaryExpression binaryExpression = (PsiBinaryExpression) newExpression;
            String negatedComparison = ComparisonUtils.getNegatedComparison(binaryExpression.getOperationTokenType());
            PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            assert rhs != null;
            expString = lhs.getText() + negatedComparison + rhs.getText();
        }
        else {
            if (ParenthesesUtils.getPrecedence(newExpression) > ParenthesesUtils.PREFIX_PRECEDENCE) {
                expString = "!(" + newExpressionText + ')';
            }
            else {
                expString = '!' + newExpressionText;
            }
        }
        PsiExpression newCall = factory.createExpressionFromText(expString, expression);
        assert expressionToReplace != null;
        PsiElement insertedElement = expressionToReplace.replace(newCall);
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceExpressionWithNegatedExpressionString(@Nonnull String newExpression, @Nonnull PsiExpression expression) {
        Project project = expression.getProject();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        PsiElementFactory factory = psiFacade.getElementFactory();
        PsiExpression expressionToReplace = expression;
        String expString;
        if (BoolUtils.isNegated(expression)) {
            expressionToReplace = BoolUtils.findNegation(expressionToReplace);
            expString = newExpression;
        }
        else {
            PsiElement parent = expressionToReplace.getParent();
            while (parent instanceof PsiParenthesizedExpression) {
                expressionToReplace = (PsiExpression) parent;
                parent = parent.getParent();
            }
            expString = "!(" + newExpression + ')';
        }
        PsiExpression newCall = factory.createExpressionFromText(expString, expression);
        assert expressionToReplace != null;
        PsiElement insertedElement = expressionToReplace.replace(newCall);
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceStatement(@NonNls @Nonnull String newStatementText, @NonNls @Nonnull PsiStatement statement) {
        Project project = statement.getProject();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiStatement newStatement = factory.createStatementFromText(newStatementText, statement);
        PsiElement insertedElement = statement.replace(newStatement);
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(insertedElement);
    }

    protected static void replaceStatementAndShorten(@NonNls @Nonnull String newStatementText, @NonNls @Nonnull PsiStatement statement) {
        Project project = statement.getProject();
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        PsiElementFactory factory = psiFacade.getElementFactory();
        PsiStatement newStatement = factory.createStatementFromText(newStatementText, statement);
        PsiElement insertedElement = statement.replace(newStatement);
        JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
        PsiElement shortenedElement = javaCodeStyleManager.shortenClassReferences(insertedElement);
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        codeStyleManager.reformat(shortenedElement);
    }

    protected static void addStatementBefore(String newStatementText, PsiReturnStatement anchor) {
        Project project = anchor.getProject();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiStatement newStatement = factory.createStatementFromText(newStatementText, anchor);
        PsiElement addedStatement = anchor.getParent().addBefore(newStatement, anchor);
        CodeStyleManager.getInstance(project).reformat(addedStatement);
    }

    @Nullable
    PsiElement findMatchingElement(@Nullable PsiElement element, Editor editor) {
        while (element != null) {
            if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) {
                break;
            }
            if (predicate instanceof PsiElementEditorPredicate) {
                if (((PsiElementEditorPredicate) predicate).satisfiedBy(element, editor)) {
                    return element;
                }
            }
            else if (predicate.satisfiedBy(element)) {
                return element;
            }
            element = element.getParent();
            if (element instanceof PsiFile) {
                break;
            }
        }
        return null;
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
        return findMatchingElement(element, editor) != null;
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    private static boolean isWritable(Project project, PsiElement element) {
        VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
        if (virtualFile == null) {
            return true;
        }
        ReadonlyStatusHandler readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project);
        ReadonlyStatusHandler.OperationStatus operationStatus = readonlyStatusHandler.ensureFilesWritable(virtualFile);
        return !operationStatus.hasReadonlyFiles();
    }

    @Override
    @Nonnull
    public abstract LocalizeValue getText();
}