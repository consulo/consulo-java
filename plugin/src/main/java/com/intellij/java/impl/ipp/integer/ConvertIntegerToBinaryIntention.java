/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertIntegerToBinaryIntention", fileExtensions = "java", categories = {"Java", "Numbers"})
public class ConvertIntegerToBinaryIntention extends ConvertNumberIntentionBase {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.convertIntegerToBinaryIntentionName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ConvertIntegerToBinaryPredicate();
    }

    @Override
    protected String convertValue(Number value, PsiType type, boolean negated) {
        if (PsiType.INT.equals(type)) {
            int intValue = negated ? -value.intValue() : value.intValue();
            return "0b" + Integer.toBinaryString(intValue);
        }
        else if (PsiType.LONG.equals(type)) {
            long longValue = negated ? -value.longValue() : value.longValue();
            return "0b" + Long.toBinaryString(longValue) + "L";
        }

        return null;
    }
}
