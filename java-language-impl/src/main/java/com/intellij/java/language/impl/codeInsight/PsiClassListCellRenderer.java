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
package com.intellij.java.language.impl.codeInsight;

import consulo.language.editor.ui.PsiElementListCellRenderer;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassOwner;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nullable;

public class PsiClassListCellRenderer extends PsiElementListCellRenderer<PsiClass> {
  public static final PsiClassListCellRenderer INSTANCE = new PsiClassListCellRenderer();

  @Override
  public String getElementText(PsiClass element) {
    return ClassPresentationUtil.getNameForClass(element, false);
  }

  @Override
  protected String getContainerText(PsiClass element, final String name) {
    return getContainerTextStatic(element);
  }

  @Nullable
  public static String getContainerTextStatic(final PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file instanceof PsiClassOwner) {
      String packageName = ((PsiClassOwner) file).getPackageName();
      if (packageName.isEmpty()) {
        return null;
      }
      return "(" + packageName + ")";
    }
    return null;
  }

  @Override
  public int getIconFlags() {
    return 0;
  }
}
