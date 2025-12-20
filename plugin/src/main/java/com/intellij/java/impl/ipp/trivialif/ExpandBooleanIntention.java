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
package com.intellij.java.impl.ipp.trivialif;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertToNestedIfIntention", fileExtensions = "java", categories = {"Java", "Boolean"})
public class ExpandBooleanIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.expandBooleanIntentionName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ExpandBooleanPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        PsiStatement containingStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
        if (containingStatement == null) {
            return;
        }
        if (ExpandBooleanPredicate.isBooleanAssignment(containingStatement)) {
            PsiExpressionStatement assignmentStatement = (PsiExpressionStatement) containingStatement;
            PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) assignmentStatement.getExpression();
            PsiExpression rhs = assignmentExpression.getRExpression();
            if (rhs == null) {
                return;
            }
            PsiExpression lhs = assignmentExpression.getLExpression();
            if (ErrorUtil.containsDeepError(lhs) || ErrorUtil.containsDeepError(rhs)) {
                return;
            }
            String rhsText = rhs.getText();
            String lhsText = lhs.getText();
            PsiJavaToken sign = assignmentExpression.getOperationSign();
            String signText = sign.getText();
            String conditionText;
            if (signText.length() == 2) {
                conditionText = lhsText + signText.charAt(0) + rhsText;
            }
            else {
                conditionText = rhsText;
            }
            @NonNls String statement = "if(" + conditionText + ") " + lhsText + " = true; else " + lhsText + " = false;";
            replaceStatement(statement, containingStatement);
        }
        else if (ExpandBooleanPredicate.isBooleanReturn(containingStatement)) {
            PsiReturnStatement returnStatement = (PsiReturnStatement) containingStatement;
            PsiExpression returnValue = returnStatement.getReturnValue();
            if (returnValue == null) {
                return;
            }
            if (ErrorUtil.containsDeepError(returnValue)) {
                return;
            }
            String valueText = returnValue.getText();
            @NonNls String statement = "if(" + valueText + ") return true; else return false;";
            replaceStatement(statement, containingStatement);
        }
        else if (ExpandBooleanPredicate.isBooleanDeclaration(containingStatement)) {
            PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement) containingStatement;
            PsiElement declaredElement = declarationStatement.getDeclaredElements()[0];
            if (!(declaredElement instanceof PsiLocalVariable)) {
                return;
            }
            PsiLocalVariable variable = (PsiLocalVariable) declaredElement;
            PsiExpression initializer = variable.getInitializer();
            if (initializer == null) {
                return;
            }
            String name = variable.getName();
            @NonNls String newStatementText = "if(" + initializer.getText() + ") " + name + "=true; else " + name + "=false;";
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(containingStatement.getProject());
            PsiStatement newStatement = factory.createStatementFromText(newStatementText, containingStatement);
            declarationStatement.getParent().addAfter(newStatement, declarationStatement);
            initializer.delete();
        }
    }
}