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
package com.intellij.java.impl.ipp.comment;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiParserFacade;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ChangeToEndOfLineCommentIntention", fileExtensions = "java", categories = {"Java", "Comments"})
public class ChangeToEndOfLineCommentIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.changeToEndOfLineCommentIntentionName();
    }

    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new CStyleCommentPredicate();
    }

    public void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        PsiComment comment = (PsiComment) element;
        Project project = comment.getProject();
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        PsiElement parent = comment.getParent();
        assert parent != null;
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        String commentText = comment.getText();
        PsiElement whitespace = comment.getNextSibling();
        String text = commentText.substring(2, commentText.length() - 2);
        String[] lines = text.split("\n");
        for (int i = lines.length - 1; i >= 1; i--) {
            PsiComment nextComment =
                factory.createCommentFromText("//" + lines[i].trim(),
                    parent);
            parent.addAfter(nextComment, comment);
            if (whitespace != null) {
                PsiParserFacade parserFacade = PsiParserFacade.SERVICE.getInstance(project);
                PsiElement newWhiteSpace =
                    parserFacade.createWhiteSpaceFromText(whitespace.getText());
                parent.addAfter(newWhiteSpace, comment);
            }
        }
        PsiComment newComment =
            factory.createCommentFromText("//" + lines[0], parent);
        PsiElement replacedComment = comment.replace(newComment);
        codeStyleManager.reformat(replacedComment);
    }
}