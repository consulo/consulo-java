/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInsight.template.postfix.templates;

import consulo.codeEditor.Editor;
import consulo.util.lang.function.Condition;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;

import jakarta.annotation.Nonnull;

import static com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.JAVA_PSI_INFO;

public class FormatPostfixTemplate extends JavaStatementWrapPostfixTemplate {
  private static final Condition<PsiElement> IS_STRING = new Condition<PsiElement>() {
    @Override
    public boolean value(PsiElement expr) {
      if (!(expr instanceof PsiExpression)) {
        return false;
      }
      PsiType type = ((PsiExpression)expr).getType();
      return type != null && JavaClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText());
    }
  };


  public FormatPostfixTemplate() {
    super("format", "String.format(expr);", JAVA_PSI_INFO, IS_STRING);
  }

  @Override
  protected void afterExpand(@Nonnull PsiElement newElement, @Nonnull Editor editor) {
    editor.getCaretModel().moveToOffset(newElement.getTextRange().getEndOffset() - 2);
    JavaPostfixTemplateProvider.doNotDeleteSemicolon(newElement.getContainingFile());
  }

  @Nonnull
  @Override
  protected String getHead() {
    return "String.format(";
  }

  @Nonnull
  @Override
  protected String getTail() {
    return ", );";
  }
}
