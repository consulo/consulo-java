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
package com.intellij.java.impl.ipp.braces;

import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.PsiBlockStatement;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiDeclarationStatement;
import com.intellij.java.language.psi.PsiStatement;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.RemoveBracesIntention", fileExtensions = "java", categories = {"Java", "Declaration"})
public class RemoveBracesIntention extends BaseBracesIntention {

    @Override
    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return element -> {
            final PsiStatement statement = getSurroundingStatement(element);
            if (statement == null || !(statement instanceof PsiBlockStatement)) {
                return false;
            }

            final PsiStatement[] statements = ((PsiBlockStatement) statement).getCodeBlock().getStatements();
            if (statements.length != 1 || statements[0] instanceof PsiDeclarationStatement) {
                return false;
            }
            final PsiFile file = statement.getContainingFile();
            //this intention doesn't work in JSP files, as it can't tell about tags
            // inside the braces
            return true;
            // return !JspPsiUtil.isInJspFile(file);
        };
    }

    @Nonnull
    @Override
    protected LocalizeValue getMessageKey(String keyword) {
        return IntentionPowerPackLocalize.removeBracesIntentionName(keyword);
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.removeBracesIntentionFamilyName();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        final PsiStatement body = getSurroundingStatement(element);
        if (body == null || !(body instanceof PsiBlockStatement)) {
            return;
        }
        final PsiBlockStatement blockStatement = (PsiBlockStatement) body;

        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        final PsiStatement statement = statements[0];

        handleComments(blockStatement, codeBlock);

        final String text = statement.getText();
        replaceStatement(text, blockStatement);
    }

    private static void handleComments(PsiBlockStatement blockStatement, PsiCodeBlock codeBlock) {
        final PsiElement parent = blockStatement.getParent();
        assert parent != null;
        final PsiElement grandParent = parent.getParent();
        assert grandParent != null;
        PsiElement sibling = codeBlock.getFirstChild();
        assert sibling != null;
        sibling = sibling.getNextSibling();
        while (sibling != null) {
            if (sibling instanceof PsiComment) {
                grandParent.addBefore(sibling, parent);
            }
            sibling = sibling.getNextSibling();
        }
        final PsiElement lastChild = blockStatement.getLastChild();
        if (lastChild instanceof PsiComment) {
            final PsiElement nextSibling = parent.getNextSibling();
            grandParent.addAfter(lastChild, nextSibling);
        }
    }
}