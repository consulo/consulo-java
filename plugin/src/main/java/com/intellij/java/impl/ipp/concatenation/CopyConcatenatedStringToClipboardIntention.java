/*
 * Copyright 2008-2012 Bas Leijdekkers
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
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.ig.psiutils.ExpressionUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.util.IncorrectOperationException;
import consulo.ui.ex.awt.CopyPasteManager;
import jakarta.annotation.Nonnull;

import java.awt.datatransfer.StringSelection;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.SwapMethodCallArgumentsIntention", fileExtensions = "java", categories = {"Java", "Strings"})
public class CopyConcatenatedStringToClipboardIntention extends Intention {

  @Override
  @Nonnull
  protected PsiElementPredicate getElementPredicate() {
    return new SimpleStringConcatenationPredicate(false);
  }

  @Override
  protected void processIntention(@jakarta.annotation.Nonnull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiPolyadicExpression)) {
      return;
    }
    PsiPolyadicExpression concatenationExpression = (PsiPolyadicExpression)element;
    final IElementType tokenType = concatenationExpression.getOperationTokenType();
    if (tokenType != JavaTokenType.PLUS) {
      return;
    }
    final PsiType type = concatenationExpression.getType();
    if (type == null || !type.equalsToText("java.lang.String")) {
      return;
    }
    final StringBuilder text = buildConcatenationText(concatenationExpression, new StringBuilder());
    CopyPasteManager.getInstance().setContents(new StringSelection(text.toString()));
  }

  private static StringBuilder buildConcatenationText(PsiPolyadicExpression polyadicExpression, StringBuilder out) {
    for (PsiElement element : polyadicExpression.getChildren()) {
      if (element instanceof PsiExpression) {
        final PsiExpression expression = (PsiExpression)element;
        final Object value = ExpressionUtils.computeConstantExpression(expression);
        if (value == null) {
          out.append('?');
        }
        else {
          out.append(value.toString());
        }
      }
      else if (element instanceof PsiWhiteSpace && element.getText().contains("\n") &&
               (out.length() == 0 || out.charAt(out.length() - 1) != '\n')) {
        out.append('\n');
      }
    }
    return out;
  }
}
