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
package com.intellij.java.impl.codeInsight.folding.impl;

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.impl.codeInsight.ExpectedTypesProvider;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.codeInsight.folding.impl.JavaFoldingBuilderBase;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiNewExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.EditorFontType;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.ex.awt.FontUtil;

import javax.annotation.Nonnull;
import java.awt.*;

public class JavaFoldingBuilder extends JavaFoldingBuilderBase {
  @Override
  protected boolean isBelowRightMargin(@Nonnull Project project, int lineLength) {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    return lineLength <= settings.getRightMargin(JavaLanguage.INSTANCE);
  }

  @Override
  protected boolean shouldShowExplicitLambdaType(@Nonnull PsiAnonymousClass anonymousClass, @Nonnull PsiNewExpression expression) {
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiReferenceExpression || parent instanceof PsiAssignmentExpression) {
      return true;
    }

    ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes(expression, false);
    return types.length != 1 || !types[0].getType().equals(anonymousClass.getBaseClassType());
  }

  @Override
  @Nonnull
  protected String rightArrow() {
    return getRightArrow();
  }

  @Nonnull
  public static String getRightArrow() {
    Font font = EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
    return FontUtil.rightArrow(font);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }
}

