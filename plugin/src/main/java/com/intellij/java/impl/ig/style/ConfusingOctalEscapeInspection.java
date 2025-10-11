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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.PsiLiteralExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ConfusingOctalEscapeInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "ConfusingOctalEscapeSequence";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.confusingOctalEscapeSequenceDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.confusingOctalEscapeSequenceProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConfusingOctalEscapeVisitor();
  }

  private static class ConfusingOctalEscapeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@Nonnull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      if (!ExpressionUtils.hasStringType(expression)) {
        return;
      }
      final String text = expression.getText();
      int escapeStart = -1;
      while (true) {
        escapeStart = text.indexOf((int)'\\', escapeStart + 1);
        if (escapeStart < 0) {
          return;
        }
        if (escapeStart > 0 && text.charAt(escapeStart - 1) == '\\') {
          continue;
        }
        boolean isEscape = true;
        final int textLength = text.length();
        int nextChar = escapeStart + 1;
        while (nextChar < textLength && text.charAt(nextChar) == '\\') {
          isEscape = !isEscape;
          nextChar++;
        }
        if (!isEscape) {
          continue;
        }
        escapeStart = nextChar - 1;
        int length = 1;
        while (escapeStart + length < textLength) {
          final char c = text.charAt(escapeStart + length);
          if (!Character.isDigit(c)) {
            break;
          }
          if (c == (int)'8' || c == (int)'9') {
            registerErrorAtOffset(expression, escapeStart, length);
            break;
          } else if (length > 3) {
            registerErrorAtOffset(expression, escapeStart, length);
            break;
          }
          length++;
        }
      }
    }
  }
}