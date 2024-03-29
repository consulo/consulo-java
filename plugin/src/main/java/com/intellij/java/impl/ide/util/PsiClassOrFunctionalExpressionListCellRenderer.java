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
package com.intellij.java.impl.ide.util;

import consulo.language.editor.ui.PsiElementListCellRenderer;
import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import consulo.language.psi.NavigatablePsiElement;

public class PsiClassOrFunctionalExpressionListCellRenderer extends PsiElementListCellRenderer<NavigatablePsiElement> {
  @Override
  public String getElementText(NavigatablePsiElement element) {
    return element instanceof PsiClass ? ClassPresentationUtil.getNameForClass((PsiClass) element, false) : PsiExpressionTrimRenderer.render((PsiExpression) element);
  }

  @Override
  protected String getContainerText(NavigatablePsiElement element, final String name) {
    return PsiClassListCellRenderer.getContainerTextStatic(element);
  }

  @Override
  protected int getIconFlags() {
    return 0;
  }
}
