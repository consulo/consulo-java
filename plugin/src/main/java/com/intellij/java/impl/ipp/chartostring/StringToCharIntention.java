/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.chartostring;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.StringToCharIntention", fileExtensions = "java", categories = {"Java", "Strings"})
public class StringToCharIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.stringToCharIntentionName();
    }

    @Override
    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new StringToCharPredicate();
    }

    @Override
    public void processIntention(PsiElement element)
        throws IncorrectOperationException {
        PsiLiteralExpression stringLiteral =
            (PsiLiteralExpression) element;
        String stringLiteralText = stringLiteral.getText();
        String charLiteral = charForStringLiteral(stringLiteralText);
        replaceExpression(charLiteral, stringLiteral);
    }

    private static String charForStringLiteral(String stringLiteral) {
        if ("\"'\"".equals(stringLiteral)) {
            return "'\\''";
        }
        else if ("\"\\\"\"".equals(stringLiteral)) {
            return "'\"'";
        }
        else {
            return '\'' +
                stringLiteral.substring(1, stringLiteral.length() - 1) +
                '\'';
        }
    }
}