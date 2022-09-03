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

import com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.util.lang.function.Condition;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.java.language.module.util.JavaClassNames;

import javax.annotation.Nonnull;

import static com.intellij.java.impl.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.JAVA_PSI_INFO;

public class SwitchStatementPostfixTemplate extends JavaStatementWrapPostfixTemplate {
  private static final Condition<PsiElement> SWITCH_TYPE = new Condition<PsiElement>() {
    @Override
    public boolean value(PsiElement expression) {
      if (!(expression instanceof PsiExpression)) {
        return false;
      }

      PsiType type = ((PsiExpression)expression).getType();

      if (type == null) return false;
      if (PsiType.INT.isAssignableFrom(type)) return true;

      if (type instanceof PsiClassType) {
        PsiClass psiClass = ((PsiClassType)type).resolve();
        if (psiClass != null && psiClass.isEnum()) return true;
      }

      if (type.equalsToText(JavaClassNames.JAVA_LANG_STRING)) {
        if (ApplicationManager.getApplication().isUnitTestMode()) return true; // todo: mock jdk 6 and 7
        PsiFile containingFile = expression.getContainingFile();
        if (containingFile instanceof PsiJavaFile) {
          LanguageLevel level = ((PsiJavaFile)containingFile).getLanguageLevel();
          if (level.isAtLeast(LanguageLevel.JDK_1_7)) return true;
        }
      }

      return false;
    }
  };

  public SwitchStatementPostfixTemplate() {
    super("switch", "switch (expr)", JAVA_PSI_INFO, SWITCH_TYPE);
  }

  @Override
  protected void afterExpand(@Nonnull PsiElement newStatement, @Nonnull Editor editor) {
    JavaPostfixTemplatesUtils.formatPsiCodeBlock(newStatement, editor);
  }

  @Nonnull
  @Override
  protected String getHead() {
    return "switch (";
  }

  @Nonnull
  @Override
  protected String getTail() {
    return ") {\nst;\n}";
  }
}
