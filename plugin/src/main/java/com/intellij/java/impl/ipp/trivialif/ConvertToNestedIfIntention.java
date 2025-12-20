/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.ipp.trivialif;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author anna
 * Date: 2/2/12
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertToNestedIfIntention", fileExtensions = "java", categories = {"Java", "Boolean"})
public class ConvertToNestedIfIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.convertToNestedIfIntentionName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new PsiElementPredicate() {

            public boolean satisfiedBy(PsiElement element) {
                if (!(element instanceof PsiReturnStatement)) {
                    return false;
                }
                PsiReturnStatement returnStatement = (PsiReturnStatement) element;
                PsiExpression returnValue = ParenthesesUtils.stripParentheses(returnStatement.getReturnValue());
                if (!(returnValue instanceof PsiPolyadicExpression)) {
                    return false;
                }
                PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) returnValue;
                IElementType tokenType = polyadicExpression.getOperationTokenType();
                return tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR;
            }
        };
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) {
        PsiReturnStatement returnStatement = (PsiReturnStatement) element;
        PsiExpression returnValue = returnStatement.getReturnValue();
        if (returnValue == null || ErrorUtil.containsDeepError(returnValue)) {
            return;
        }
        String newStatementText = buildIf(returnValue, new StringBuilder()).toString();
        Project project = returnStatement.getProject();
        PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiBlockStatement blockStatement = (PsiBlockStatement) elementFactory.createStatementFromText("{" + newStatementText + "}", returnStatement);
        PsiElement parent = returnStatement.getParent();
        for (PsiStatement st : blockStatement.getCodeBlock().getStatements()) {
            CodeStyleManager.getInstance(project).reformat(parent.addBefore(st, returnStatement));
        }
        replaceStatement("return false;", returnStatement);
    }

    private static StringBuilder buildIf(@Nullable PsiExpression expression, StringBuilder out) {
        if (expression instanceof PsiPolyadicExpression) {
            PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) expression;
            PsiExpression[] operands = polyadicExpression.getOperands();
            IElementType tokenType = polyadicExpression.getOperationTokenType();
            if (JavaTokenType.ANDAND.equals(tokenType)) {
                for (PsiExpression operand : operands) {
                    buildIf(operand, out);
                }
                if (!StringUtil.endsWith(out, "return true;")) {
                    out.append("return true;");
                }
                return out;
            }
            else if (JavaTokenType.OROR.equals(tokenType)) {
                for (PsiExpression operand : operands) {
                    buildIf(operand, out);
                    if (!StringUtil.endsWith(out, "return true;")) {
                        out.append("return true;");
                    }
                }
                return out;
            }
        }
        else if (expression instanceof PsiParenthesizedExpression) {
            PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) expression;
            buildIf(parenthesizedExpression.getExpression(), out);
            return out;
        }
        if (expression != null) {
            out.append("if(").append(expression.getText()).append(")");
        }
        return out;
    }
}
