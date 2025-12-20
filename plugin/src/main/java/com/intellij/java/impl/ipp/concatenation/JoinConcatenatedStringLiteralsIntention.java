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
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.JoinConcatenatedStringLiteralsIntention", fileExtensions = "java", categories = {"Java", "Strings"})
public class JoinConcatenatedStringLiteralsIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.joinConcatenatedStringLiteralsIntentionName();
    }

    @Override
    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new StringConcatPredicate();
    }

    @Override
    public void processIntention(PsiElement element) throws IncorrectOperationException {
        if (element instanceof PsiWhiteSpace) {
            element = element.getPrevSibling();
        }
        if (!(element instanceof PsiJavaToken)) {
            return;
        }
        PsiJavaToken token = (PsiJavaToken) element;
        PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) element.getParent();
        StringBuilder newExpression = new StringBuilder();
        PsiElement[] children = polyadicExpression.getChildren();
        List<PsiElement> buffer = new ArrayList(3);
        for (PsiElement child : children) {
            if (child instanceof PsiJavaToken) {
                if (token.equals(child)) {
                    PsiLiteralExpression literalExpression = (PsiLiteralExpression) buffer.get(0);
                    Object value = literalExpression.getValue();
                    assert value != null;
                    newExpression.append('"').append(StringUtil.escapeStringCharacters(value.toString()));
                }
                else {
                    for (PsiElement bufferedElement : buffer) {
                        newExpression.append(bufferedElement.getText());
                    }
                    buffer.clear();
                    newExpression.append(child.getText());
                }
            }
            else if (child instanceof PsiLiteralExpression) {
                if (buffer.isEmpty()) {
                    buffer.add(child);
                }
                else {
                    PsiLiteralExpression literalExpression = (PsiLiteralExpression) child;
                    Object value = literalExpression.getValue();
                    assert value != null;
                    newExpression.append(StringUtil.escapeStringCharacters(value.toString())).append('"');
                    buffer.clear();
                }
            }
            else {
                if (buffer.isEmpty()) {
                    newExpression.append(child.getText());
                }
                else {
                    buffer.add(child);
                }
            }
        }
        for (PsiElement bufferedElement : buffer) {
            newExpression.append(bufferedElement.getText());
        }
        replaceExpression(newExpression.toString(), polyadicExpression);
    }
}
