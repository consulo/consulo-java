/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.hint;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.ExpressionTypeProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SyntaxTraverser;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author gregsh
 */
@ExtensionImpl
public class JavaTypeProvider extends ExpressionTypeProvider<PsiExpression> {
  @Nonnull
  @Override
  public String getInformationHint(@Nonnull PsiExpression element) {
    PsiType type = element.getType();
    String text = type == null ? "<unknown>" : type.getCanonicalText();
    return StringUtil.escapeXml(text);
  }

  @Nonnull
  @Override
  public String getErrorHint() {
    return "No expression found";
  }

  @Nonnull
  @Override
  public List<PsiExpression> getExpressionsAt(@jakarta.annotation.Nonnull PsiElement elementAt) {
    return SyntaxTraverser.psiApi().parents(elementAt).filter(PsiExpression.class).toList();
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
