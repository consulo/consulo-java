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
package com.intellij.java.impl.ide.util;

import consulo.language.editor.ui.PsiElementListCellRenderer;
import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.ide.impl.psi.util.PsiFormatUtilBase;
import consulo.ui.image.Image;

public class MethodOrFunctionalExpressionCellRenderer extends PsiElementListCellRenderer<NavigatablePsiElement> {
  private final PsiClassListCellRenderer myClassListCellRenderer = new PsiClassListCellRenderer();
  private final MethodCellRenderer myMethodCellRenderer;

  public MethodOrFunctionalExpressionCellRenderer(boolean showMethodNames) {
    this(showMethodNames, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS);
  }

  public MethodOrFunctionalExpressionCellRenderer(boolean showMethodNames, @PsiFormatUtil.FormatMethodOptions int options) {
    myMethodCellRenderer = new MethodCellRenderer(showMethodNames, options);
  }

  public String getElementText(NavigatablePsiElement element) {
    return element instanceof PsiMethod ? myMethodCellRenderer.getElementText((PsiMethod) element) : PsiExpressionTrimRenderer.render((PsiExpression) element);
  }

  protected Image getIcon(PsiElement element) {
    return element instanceof PsiMethod ? myMethodCellRenderer.getIcon(element) : super.getIcon(element);
  }

  public String getContainerText(final NavigatablePsiElement element, final String name) {
    return element instanceof PsiMethod ? myMethodCellRenderer.getContainerText((PsiMethod) element, name) : PsiClassListCellRenderer.getContainerTextStatic(element);
  }

  public int getIconFlags() {
    return myClassListCellRenderer.getIconFlags();
  }
}
