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
import com.intellij.java.language.psi.PsiStatement;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.AddBracesIntention", fileExtensions = "java", categories = {"Java", "Declaration"})
public class AddBracesIntention extends BaseBracesIntention {

    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return element -> {
            final PsiStatement statement = getSurroundingStatement(element);
            return statement != null && !(statement instanceof PsiBlockStatement);
        };
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.addBracesIntentionFamilyName();
    }

    @Nonnull
    @Override
    protected LocalizeValue getMessageKey(String keyword) {
        return IntentionPowerPackLocalize.addBracesIntentionName(keyword);
    }

    protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        final PsiStatement statement = getSurroundingStatement(element);
        if (statement == null) {
            return;
        }
        final String newStatement = "{\n" + statement.getText() + "\n}";
        replaceStatement(newStatement, statement);
    }
}