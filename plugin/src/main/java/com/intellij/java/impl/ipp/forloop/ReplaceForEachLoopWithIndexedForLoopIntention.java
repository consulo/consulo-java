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
package com.intellij.java.impl.ipp.forloop;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceForEachLoopWithIndexedForLoopIntention", fileExtensions = "java", categories = {"Java", "Control Flow"})
public class ReplaceForEachLoopWithIndexedForLoopIntention extends Intention {

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.replaceForEachLoopWithIndexedForLoopIntentionName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new IndexedForEachLoopPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        PsiForeachStatement statement = (PsiForeachStatement) element.getParent();
        if (statement == null) {
            return;
        }
        PsiExpression iteratedValue = statement.getIteratedValue();
        if (iteratedValue == null) {
            return;
        }
        PsiParameter iterationParameter =
            statement.getIterationParameter();
        PsiType type = iterationParameter.getType();
        PsiType iteratedValueType = iteratedValue.getType();
        if (iteratedValueType == null) {
            return;
        }
        boolean isArray = iteratedValueType instanceof PsiArrayType;
        PsiElement grandParent = statement.getParent();
        PsiStatement context;
        if (grandParent instanceof PsiLabeledStatement) {
            context = (PsiStatement) grandParent;
        }
        else {
            context = statement;
        }
        String iteratedValueText = getReferenceToIterate(iteratedValue, context);
        @NonNls StringBuilder newStatement = new StringBuilder();
        String indexText = createVariableName("i", PsiType.INT, statement);
        createForLoopDeclaration(statement, iteratedValue, isArray, iteratedValueText, newStatement, indexText);
        Project project = statement.getProject();
        JavaCodeStyleSettings codeStyleSettings =
            CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
        if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
            newStatement.append("final ");
        }
        newStatement.append(type.getCanonicalText());
        newStatement.append(' ');
        newStatement.append(iterationParameter.getName());
        newStatement.append(" = ");
        newStatement.append(iteratedValueText);
        if (isArray) {
            newStatement.append('[');
            newStatement.append(indexText);
            newStatement.append("];");
        }
        else {
            newStatement.append(".get(");
            newStatement.append(indexText);
            newStatement.append(");");
        }
        PsiStatement body = statement.getBody();
        if (body == null) {
            return;
        }
        if (body instanceof PsiBlockStatement) {
            PsiCodeBlock block = ((PsiBlockStatement) body).getCodeBlock();
            PsiElement[] children = block.getChildren();
            for (int i = 1; i < children.length - 1; i++) {
                //skip the braces
                newStatement.append(children[i].getText());
            }
        }
        else {
            newStatement.append(body.getText());
        }
        newStatement.append('}');
        replaceStatementAndShorten(newStatement.toString(), statement);
    }

    protected void createForLoopDeclaration(PsiForeachStatement statement,
                                            PsiExpression iteratedValue,
                                            boolean array,
                                            String iteratedValueText, StringBuilder newStatement,
                                            String indexText) {
        newStatement.append("for(int ");
        newStatement.append(indexText);
        newStatement.append(" = 0; ");
        newStatement.append(indexText);
        newStatement.append('<');
        if (iteratedValue instanceof PsiTypeCastExpression) {
            newStatement.append('(');
            newStatement.append(iteratedValueText);
            newStatement.append(')');
        }
        else {
            newStatement.append(iteratedValueText);
        }
        if (array) {
            newStatement.append(".length");
        }
        else {
            newStatement.append(".size()");
        }
        newStatement.append(';');
        newStatement.append(indexText);
        newStatement.append("++)");
        newStatement.append("{ ");
    }

    private static String getVariableName(PsiExpression expression) {
        if (expression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) expression;
            PsiReferenceExpression methodExpression =
                methodCallExpression.getMethodExpression();
            String name = methodExpression.getReferenceName();
            if (name == null) {
                return null;
            }
            if (name.startsWith("to") && name.length() > 2) {
                return StringUtil.decapitalize(name.substring(2));
            }
            else if (name.startsWith("get") && name.length() > 3) {
                return StringUtil.decapitalize(name.substring(3));
            }
            else {
                return name;
            }
        }
        else if (expression instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression castExpression =
                (PsiTypeCastExpression) expression;
            PsiExpression operand = castExpression.getOperand();
            return getVariableName(operand);
        }
        else if (expression instanceof PsiArrayAccessExpression) {
            PsiArrayAccessExpression arrayAccessExpression =
                (PsiArrayAccessExpression) expression;
            PsiExpression arrayExpression =
                arrayAccessExpression.getArrayExpression();
            return StringUtil.unpluralize(getVariableName(arrayExpression));
        }
        else if (expression instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression =
                (PsiParenthesizedExpression) expression;
            PsiExpression innerExpression =
                parenthesizedExpression.getExpression();
            return getVariableName(innerExpression);
        }
        else if (expression instanceof PsiJavaCodeReferenceElement) {
            PsiJavaCodeReferenceElement referenceElement =
                (PsiJavaCodeReferenceElement) expression;
            String referenceName = referenceElement.getReferenceName();
            if (referenceName == null) {
                return expression.getText();
            }
            return referenceName;
        }
        return null;
    }

    private static String getReferenceToIterate(
        PsiExpression expression, PsiElement context) {
        if (expression instanceof PsiMethodCallExpression ||
            expression instanceof PsiTypeCastExpression ||
            expression instanceof PsiArrayAccessExpression ||
            expression instanceof PsiNewExpression) {
            String variableName = getVariableName(expression);
            return createVariable(variableName, expression, context);
        }
        else if (expression instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression =
                (PsiParenthesizedExpression) expression;
            PsiExpression innerExpression =
                parenthesizedExpression.getExpression();
            return getReferenceToIterate(innerExpression, context);
        }
        else if (expression instanceof PsiJavaCodeReferenceElement) {
            PsiJavaCodeReferenceElement referenceElement =
                (PsiJavaCodeReferenceElement) expression;
            String variableName = getVariableName(expression);
            if (referenceElement.isQualified()) {
                return createVariable(variableName, expression, context);
            }
            PsiElement target = referenceElement.resolve();
            if (target instanceof PsiVariable) {
                // maybe should not do this for local variables outside of
                // anonymous classes
                return variableName;
            }
            return createVariable(variableName, expression, context);
        }
        return null;
    }

    private static String createVariable(String variableNameRoot,
                                         PsiExpression iteratedValue,
                                         PsiElement context) {
        String variableName =
            createVariableName(variableNameRoot, iteratedValue);
        Project project = context.getProject();
        PsiType iteratedValueType = iteratedValue.getType();
        assert iteratedValueType != null;
        PsiElementFactory elementFactory =
            JavaPsiFacade.getElementFactory(project);
        PsiDeclarationStatement declarationStatement =
            elementFactory.createVariableDeclarationStatement(variableName,
                iteratedValueType, iteratedValue);
        context.getParent().addBefore(declarationStatement, context);
        return variableName;
    }

    public static String createVariableName(
        @Nullable String baseName,
        @Nonnull PsiExpression assignedExpression) {
        Project project = assignedExpression.getProject();
        JavaCodeStyleManager codeStyleManager =
            JavaCodeStyleManager.getInstance(project);
        SuggestedNameInfo names =
            codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE,
                baseName, assignedExpression, null);
        if (names.names.length == 0) {
            return codeStyleManager.suggestUniqueVariableName(baseName,
                assignedExpression, true);
        }
        return codeStyleManager.suggestUniqueVariableName(names.names[0],
            assignedExpression, true);
    }

    public static String createVariableName(@Nullable String baseName,
                                            @Nonnull PsiType type,
                                            @Nonnull PsiElement context) {
        Project project = context.getProject();
        JavaCodeStyleManager codeStyleManager =
            JavaCodeStyleManager.getInstance(project);
        SuggestedNameInfo names =
            codeStyleManager.suggestVariableName(
                VariableKind.LOCAL_VARIABLE, baseName, null, type);
        if (names.names.length == 0) {
            return codeStyleManager.suggestUniqueVariableName(baseName,
                context, true);
        }
        return codeStyleManager.suggestUniqueVariableName(names.names[0],
            context, true);
    }
}