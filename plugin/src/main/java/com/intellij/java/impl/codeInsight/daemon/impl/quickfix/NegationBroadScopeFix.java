/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author cdr
 * if (!a == b) ...  =>  if (!(a == b)) ...
 */

public class NegationBroadScopeFix implements SyntheticIntentionAction {
    private final PsiPrefixExpression myPrefixExpression;

    public NegationBroadScopeFix(PsiPrefixExpression prefixExpression) {
        myPrefixExpression = prefixExpression;
    }

    @Override
    @Nonnull
    public LocalizeValue getText() {
        String text = myPrefixExpression.getOperand().getText();
        text += " ";
        PsiElement parent = myPrefixExpression.getParent();
        String operation = parent instanceof PsiInstanceOfExpression
            ? PsiKeyword.INSTANCEOF
            : ((PsiBinaryExpression) parent).getOperationSign().getText();
        text += operation + " ";

        String rop;
        if (parent instanceof PsiInstanceOfExpression) {
            PsiTypeElement type = ((PsiInstanceOfExpression) parent).getCheckType();
            rop = type == null ? "" : type.getText();
        }
        else {
            PsiExpression rOperand = ((PsiBinaryExpression) parent).getROperand();
            rop = rOperand == null ? "" : rOperand.getText();
        }

        text += rop;
        return JavaQuickFixLocalize.negationBroaderScopeText(text);
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (myPrefixExpression == null || !myPrefixExpression.isValid()) {
            return false;
        }

        PsiElement parent = myPrefixExpression.getParent();
        if (parent instanceof PsiInstanceOfExpression && ((PsiInstanceOfExpression) parent).getOperand() == myPrefixExpression) {
            return true;
        }
        if (!(parent instanceof PsiBinaryExpression)) {
            return false;
        }
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression) parent;
        return binaryExpression.getLOperand() == myPrefixExpression && TypeConversionUtil.isBooleanType(binaryExpression.getType());
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(myPrefixExpression)) {
            return;
        }
        PsiExpression operand = myPrefixExpression.getOperand();
        PsiElement unnegated = myPrefixExpression.replace(operand);
        PsiElement parent = unnegated.getParent();
        PsiElementFactory factory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();

        PsiPrefixExpression negated = (PsiPrefixExpression) factory.createExpressionFromText("!(xxx)", parent);
        PsiParenthesizedExpression parentheses = (PsiParenthesizedExpression) negated.getOperand();
        parentheses.getExpression().replace(parent.copy());
        parent.replace(negated);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
