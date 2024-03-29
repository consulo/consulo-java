/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.source.tree.injected;

import consulo.document.util.ProperTextRange;
import consulo.document.util.TextRange;
import consulo.language.psi.LiteralTextEscaper;
import consulo.language.psi.PsiLanguageInjectionHost;
import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import jakarta.annotation.Nonnull;

/**
 * @author cdr
*/
public class StringLiteralEscaper<T extends PsiLanguageInjectionHost> extends LiteralTextEscaper<T> {
  private int[] outSourceOffsets;

  public StringLiteralEscaper(T host) {
    super(host);
  }

  @Override
  public boolean decode(@Nonnull final TextRange rangeInsideHost, @Nonnull StringBuilder outChars) {
    ProperTextRange.assertProperRange(rangeInsideHost);
    String subText = rangeInsideHost.substring(myHost.getText());
    outSourceOffsets = new int[subText.length()+1];
    return PsiLiteralExpressionImpl.parseStringCharacters(subText, outChars, outSourceOffsets);
  }

  @Override
  public int getOffsetInHost(int offsetInDecoded, @Nonnull final TextRange rangeInsideHost) {
    int result = offsetInDecoded < outSourceOffsets.length ? outSourceOffsets[offsetInDecoded] : -1;
    if (result == -1) return -1;
    return (result <= rangeInsideHost.getLength() ? result : rangeInsideHost.getLength()) + rangeInsideHost.getStartOffset();
  }

  @Override
  public boolean isOneLine() {
    return true;
  }
}
