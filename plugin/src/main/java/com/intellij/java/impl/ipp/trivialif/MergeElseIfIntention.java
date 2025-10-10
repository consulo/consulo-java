/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.MergeElseIfIntention", fileExtensions = "java", categories = {"Java", "Boolean"})
public class MergeElseIfIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.mergeElseIfIntentionName();
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new MergeElseIfPredicate();
    }

    public void processIntention(PsiElement element)
        throws IncorrectOperationException {
        final PsiJavaToken token = (PsiJavaToken) element;
        final PsiIfStatement parentStatement =
            (PsiIfStatement) token.getParent();
        assert parentStatement != null;
        final PsiBlockStatement elseBranch =
            (PsiBlockStatement) parentStatement.getElseBranch();
        assert elseBranch != null;
        final PsiCodeBlock elseBranchBlock = elseBranch.getCodeBlock();
        final PsiStatement elseBranchContents =
            elseBranchBlock.getStatements()[0];
        replaceStatement(elseBranchContents.getText(), elseBranch);
    }
}