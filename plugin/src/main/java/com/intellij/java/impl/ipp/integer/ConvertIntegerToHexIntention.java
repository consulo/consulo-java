/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;

import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertIntegerToHexIntention", fileExtensions = "java", categories = {"Java", "Numbers"})
public class ConvertIntegerToHexIntention extends ConvertNumberIntentionBase {
  @Override
  @Nonnull
  public PsiElementPredicate getElementPredicate() {
    return new ConvertIntegerToHexPredicate();
  }

  @Override
  protected String convertValue(final Number value, final PsiType type, final boolean negated) {
    if (PsiType.INT.equals(type)) {
      final int intValue = negated ? -value.intValue() : value.intValue();
      return "0x" + Integer.toHexString(intValue);
    }
    else if (PsiType.LONG.equals(type)) {
      final long longValue = negated ? -value.longValue() : value.longValue();
      return "0x" + Long.toHexString(longValue) + "L";
    }
    else if (PsiType.FLOAT.equals(type)) {
      final float floatValue = negated ? -value.floatValue() : value.floatValue();
      return Float.toHexString(floatValue) + 'f';
    }
    else if (PsiType.DOUBLE.equals(type)) {
      final double doubleValue = negated ? -value.doubleValue() : value.doubleValue();
      return Double.toHexString(doubleValue);
    }

    return null;
  }
}