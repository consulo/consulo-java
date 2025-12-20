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
package com.intellij.java.impl.ipp.concatenation;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.HighlightUtil;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.MakeCallChainIntoCallSequenceIntention", fileExtensions = "java", categories = {"Java", "Strings"})
public class MakeCallChainIntoCallSequenceIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.makeCallChainIntoCallSequenceIntentionName();
    }

    @Override
    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new MethodCallChainPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        List<String> callTexts = new ArrayList<String>();
        PsiExpression root = (PsiExpression) element;
        while (root instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) root;
            PsiExpressionList arguments = methodCallExpression.getArgumentList();
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            callTexts.add(methodExpression.getReferenceName() + arguments.getText());
            root = methodExpression.getQualifierExpression();
            if (root == null) {
                return;
            }
        }
        PsiType rootType = root.getType();
        if (rootType == null) {
            return;
        }
        String targetText;
        PsiStatement appendStatement;
        @NonNls String firstStatement;
        String variableDeclaration;
        boolean showRenameTemplate;
        PsiElement parent = element.getParent();
        if (parent instanceof PsiExpressionStatement) {
            targetText = root.getText();
            appendStatement = (PsiStatement) parent;
            firstStatement = null;
            variableDeclaration = null;
            showRenameTemplate = false;
        }
        else {
            PsiElement grandParent = parent.getParent();
            appendStatement = (PsiStatement) grandParent;
            if (parent instanceof PsiAssignmentExpression && grandParent instanceof PsiExpressionStatement) {
                PsiAssignmentExpression assignment = (PsiAssignmentExpression) parent;
                PsiExpression lhs = assignment.getLExpression();
                if (!(lhs instanceof PsiReferenceExpression)) {
                    return;
                }
                PsiReferenceExpression expression = (PsiReferenceExpression) lhs;
                PsiElement target = expression.resolve();
                if (!(target instanceof PsiVariable)) {
                    return;
                }
                PsiVariable variable = (PsiVariable) target;
                PsiType variableType = variable.getType();
                if (variableType.equals(rootType)) {
                    targetText = lhs.getText();
                    PsiJavaToken token = assignment.getOperationSign();
                    firstStatement = targetText + token.getText() + root.getText() + ';';
                    showRenameTemplate = false;
                }
                else {
                    targetText = "x";
                    showRenameTemplate = true;
                    Project project = element.getProject();
                    JavaCodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
                    if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
                        firstStatement = "final " + rootType.getCanonicalText() + ' ' + targetText + '=' + root.getText() + ';';
                    }
                    else {
                        firstStatement = rootType.getCanonicalText() + ' ' + targetText + '=' + root.getText() + ';';
                    }
                }
                variableDeclaration = null;
            }
            else {
                PsiDeclarationStatement declaration = (PsiDeclarationStatement) appendStatement;
                PsiVariable variable = (PsiVariable) declaration.getDeclaredElements()[0];
                PsiType variableType = variable.getType();
                if (variableType.equals(rootType)) {
                    targetText = variable.getName();
                    if (variable.hasModifierProperty(PsiModifier.FINAL)) {
                        firstStatement = "final " + variableType.getCanonicalText() + ' ' + variable.getName() + '=' + root.getText() + ';';
                    }
                    else {
                        firstStatement = variableType.getCanonicalText() + ' ' + variable.getName() + '=' + root.getText() + ';';
                    }
                    variableDeclaration = null;
                    showRenameTemplate = false;
                }
                else {
                    if (variable.hasModifierProperty(PsiModifier.FINAL)) {
                        variableDeclaration = "final " + variableType.getCanonicalText() + ' ' + variable.getName() + '=';
                    }
                    else {
                        variableDeclaration = variableType.getCanonicalText() + ' ' + variable.getName() + '=';
                    }
                    targetText = "x";
                    showRenameTemplate = true;
                    Project project = element.getProject();
                    JavaCodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
                    if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
                        firstStatement = "final " + rootType.getCanonicalText() + " x=" + root.getText() + ';';
                    }
                    else {
                        firstStatement = rootType.getCanonicalText() + " x=" + root.getText() + ';';
                    }
                }
            }
        }
        StringBuilder builder = new StringBuilder("{\n");
        if (firstStatement != null) {
            builder.append(firstStatement);
        }
        Collections.reverse(callTexts);
        for (int i = 0, size = callTexts.size(); i < size; i++) {
            String callText = callTexts.get(i);
            if (i == size - 1 && variableDeclaration != null) {
                builder.append(variableDeclaration);
            }
            builder.append(targetText).append('.').append(callText).append(";\n");
        }
        builder.append('}');
        PsiManager manager = element.getManager();
        Project project = manager.getProject();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiElement appendStatementParent = appendStatement.getParent();
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
        PsiCodeBlock codeBlock = factory.createCodeBlockFromText(builder.toString(), appendStatement);
        if (appendStatementParent instanceof PsiLoopStatement || appendStatementParent instanceof PsiIfStatement) {
            PsiElement insertedCodeBlock = appendStatement.replace(codeBlock);
            PsiCodeBlock reformattedCodeBlock = (PsiCodeBlock) codeStyleManager.reformat(insertedCodeBlock);
            if (showRenameTemplate) {
                PsiStatement[] statements = reformattedCodeBlock.getStatements();
                PsiVariable variable = (PsiVariable) ((PsiDeclarationStatement) statements[0]).getDeclaredElements()[0];
                HighlightUtil.showRenameTemplate(appendStatementParent, variable);
            }
        }
        else {
            PsiStatement[] statements = codeBlock.getStatements();
            PsiVariable variable = null;
            for (int i = 0, length = statements.length; i < length; i++) {
                PsiElement insertedStatement = appendStatementParent.addBefore(statements[i], appendStatement);
                if (i == 0 && showRenameTemplate) {
                    variable = (PsiVariable) ((PsiDeclarationStatement) insertedStatement).getDeclaredElements()[0];
                }
                codeStyleManager.reformat(insertedStatement);
            }
            appendStatement.delete();
            if (variable != null) {
                HighlightUtil.showRenameTemplate(appendStatementParent, variable);
            }
        }
    }
}
