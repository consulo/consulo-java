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
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;

import javax.annotation.Nonnull;
import java.math.BigDecimal;

/**
 * @author Konstantin Bulenkov
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConvertToPlainIntention", fileExtensions = "java", categories = {"Java", "Numbers"})
public class ConvertToPlainIntention extends ConvertNumberIntentionBase {
  @Override
  protected String convertValue(final Number value, final PsiType type, final boolean negated) {
    String text = new BigDecimal(value.toString()).toPlainString();
    if (negated) text = "-" + text;
    if (PsiType.FLOAT.equals(type)) text += "f";
    return text;
  }

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ConvertToPlainPredicate();
  }
}
