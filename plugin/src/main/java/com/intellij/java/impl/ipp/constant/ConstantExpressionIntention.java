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
package com.intellij.java.impl.ipp.constant;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.HighlightUtil;
import com.intellij.java.language.psi.PsiExpression;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConstantExpressionIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class ConstantExpressionIntention extends MutablyNamedIntention {

    @Nonnull
    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        final String text = HighlightUtil.getPresentableText(element);
        return IntentionPowerPackLocalize.constantExpressionIntentionName(text);
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.constantExpressionIntentionFamilyName();
    }

    @Override
    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new ConstantExpressionPredicate();
    }

    @Override
    public void processIntention(PsiElement element) throws IncorrectOperationException {
        final PsiExpression expression = (PsiExpression) element;
        final Object value = ExpressionUtils.computeConstantExpression(expression);
        @NonNls final String newExpression;
        if (value instanceof String) {
            final String string = (String) value;
            newExpression = '"' + StringUtil.escapeStringCharacters(string) + '"';
        }
        else if (value instanceof Character) {
            newExpression = '\'' + StringUtil.escapeStringCharacters(value.toString()) + '\'';
        }
        else if (value instanceof Long) {
            newExpression = value.toString() + 'L';
        }
        else if (value instanceof Double) {
            final double v = ((Double) value).doubleValue();
            if (Double.isNaN(v)) {
                newExpression = "java.lang.Double.NaN";
            }
            else if (Double.isInfinite(v)) {
                if (v > 0.0) {
                    newExpression = "java.lang.Double.POSITIVE_INFINITY";
                }
                else {
                    newExpression = "java.lang.Double.NEGATIVE_INFINITY";
                }
            }
            else {
                newExpression = Double.toString(v);
            }
        }
        else if (value instanceof Float) {
            final float v = ((Float) value).floatValue();
            if (Float.isNaN(v)) {
                newExpression = "java.lang.Float.NaN";
            }
            else if (Float.isInfinite(v)) {
                if (v > 0.0F) {
                    newExpression = "java.lang.Float.POSITIVE_INFINITY";
                }
                else {
                    newExpression = "java.lang.Float.NEGATIVE_INFINITY";
                }
            }
            else {
                newExpression = Float.toString(v) + 'f';
            }
        }
        else if (value == null) {
            newExpression = "null";
        }
        else {
            newExpression = String.valueOf(value);
        }
        replaceExpression(newExpression, expression);
    }
}
