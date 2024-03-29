/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.java.impl.spellchecker;

import com.intellij.java.analysis.codeInspection.BatchSuppressManager;
import com.intellij.java.analysis.codeInspection.SuppressManager;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.inspection.SuppressQuickFix;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.spellchecker.editor.inspection.SuppressibleSpellcheckingStrategy;
import consulo.language.spellcheker.tokenizer.Tokenizer;

import jakarta.annotation.Nonnull;

/**
 * @author shkate@jetbrains.com
 */
@ExtensionImpl
public class JavaSpellcheckingStrategy extends SuppressibleSpellcheckingStrategy {
  private final MethodNameTokenizerJava myMethodNameTokenizer = new MethodNameTokenizerJava();
  private final DocCommentTokenizer myDocCommentTokenizer = new DocCommentTokenizer();
  private final LiteralExpressionTokenizer myLiteralExpressionTokenizer = new LiteralExpressionTokenizer();
  private final NamedElementTokenizer myNamedElementTokenizer = new NamedElementTokenizer();

  @Nonnull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PsiMethod) {
      return myMethodNameTokenizer;
    }
    if (element instanceof PsiDocComment) {
      return myDocCommentTokenizer;
    }
    if (element instanceof PsiLiteralExpression) {
      if (SuppressManager.isSuppressedInspectionName((PsiLiteralExpression)element)) {
        return EMPTY_TOKENIZER;
      }
      return myLiteralExpressionTokenizer;
    }
    if (element instanceof PsiNamedElement) {
      return myNamedElementTokenizer;
    }

    return super.getTokenizer(element);
  }

  @Override
  public boolean isSuppressedFor(@Nonnull PsiElement element, @Nonnull String name) {
    return SuppressManager.getInstance().isSuppressedFor(element, name);
  }

  @Override
  public SuppressQuickFix[] getSuppressActions(@Nonnull PsiElement element, @Nonnull String name) {
    return BatchSuppressManager.getInstance().createBatchSuppressActions(HighlightDisplayKey.find(name));
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
