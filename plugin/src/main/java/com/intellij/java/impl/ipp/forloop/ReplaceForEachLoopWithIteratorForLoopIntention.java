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
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceForEachLoopWithIteratorForLoopIntention", fileExtensions = "java", categories = {"Java", "Control Flow"})
public class ReplaceForEachLoopWithIteratorForLoopIntention extends Intention {

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new IterableForEachLoopPredicate();
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.replaceForEachLoopWithIndexedForLoopIntentionName();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        final PsiForeachStatement statement = (PsiForeachStatement) element.getParent();
        if (statement == null) {
            return;
        }
        final PsiExpression iteratedValue = statement.getIteratedValue();
        if (iteratedValue == null) {
            return;
        }
        final PsiType iteratedValueType = iteratedValue.getType();
        if (!(iteratedValueType instanceof PsiClassType)) {
            return;
        }
        @NonNls final StringBuilder methodCall = new StringBuilder();
        if (ParenthesesUtils.getPrecedence(iteratedValue) > ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
            methodCall.append('(').append(iteratedValue.getText()).append(')');
        }
        else {
            methodCall.append(iteratedValue.getText());
        }
        methodCall.append(".iterator()");
        final Project project = statement.getProject();
        final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        final PsiExpression iteratorCall = factory.createExpressionFromText(methodCall.toString(), iteratedValue);
        final PsiType variableType = GenericsUtil.getVariableTypeByExpressionType(iteratorCall.getType());
        if (variableType == null) {
            return;
        }
        @NonNls final StringBuilder newStatement = new StringBuilder();
        newStatement.append("for(").append(variableType.getCanonicalText()).append(' ');
        final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
        final String iterator = codeStyleManager.suggestUniqueVariableName("iterator", statement, true);
        newStatement.append(iterator).append("=").append(iteratorCall.getText()).append(';');
        newStatement.append(iterator).append(".hasNext();) {");
        final JavaCodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings.class);
        if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
            newStatement.append("final ");
        }
        final PsiParameter iterationParameter = statement.getIterationParameter();
        final PsiType parameterType = iterationParameter.getType();
        final String typeText = parameterType.getCanonicalText();
        newStatement.append(typeText).append(' ').append(iterationParameter.getName()).append(" = ").append(iterator).append(".next();");
        final PsiStatement body = statement.getBody();
        if (body == null) {
            return;
        }
        if (body instanceof PsiBlockStatement) {
            final PsiCodeBlock block = ((PsiBlockStatement) body).getCodeBlock();
            final PsiElement[] children = block.getChildren();
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
}