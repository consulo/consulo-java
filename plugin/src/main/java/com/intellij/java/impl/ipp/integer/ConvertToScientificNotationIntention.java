/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.java.impl.ipp.integer;

import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * @author Konstantin Bulenkov
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertToScientificNotationIntention", fileExtensions = "java", categories = {"Java", "Numbers"})
public class ConvertToScientificNotationIntention extends ConvertNumberIntentionBase {
    private static final DecimalFormat FORMAT = new DecimalFormat("0.0#############E00", new DecimalFormatSymbols(Locale.US));

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.convertToScientificNotationIntentionName();
    }

    @Override
    protected String convertValue(final Number value, final PsiType type, final boolean negated) {
        final double doubleValue = Double.parseDouble(value.toString());  // convert to double w/o adding parasitic digits
        final String text = FORMAT.format(negated ? -doubleValue : doubleValue);
        return PsiType.FLOAT.equals(type) ? text + "f" : text;
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ConvertToScientificNotationPredicate();
    }
}
