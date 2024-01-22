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
package com.intellij.java.language.impl.psi.presentation.java;

import consulo.ui.ex.ColoredItemPresentation;
import consulo.navigation.ItemPresentation;
import consulo.codeEditor.CodeInsightColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.component.util.Iconable;
import consulo.language.psi.PsiBundle;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.ui.image.Image;
import jakarta.annotation.Nullable;

public class JavaPresentationUtil {
  private JavaPresentationUtil() {
  }

  public static ColoredItemPresentation getMethodPresentation(final PsiMethod psiMethod) {
    return new ColoredItemPresentation() {
      @Override
      public String getPresentableText() {
        return PsiFormatUtil.formatMethod(
          psiMethod,
          PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
          PsiFormatUtil.SHOW_TYPE
        );
      }

      @Override
      public TextAttributesKey getTextAttributesKey() {
        if (psiMethod.isDeprecated()) {
          return CodeInsightColors.DEPRECATED_ATTRIBUTES;
        }
        return null;
      }

      @Override
      public String getLocationString() {
        return getJavaSymbolContainerText(psiMethod);
      }

      @Override
      public Image getIcon() {
        return IconDescriptorUpdaters.getIcon(psiMethod, Iconable.ICON_FLAG_VISIBILITY);
      }
    };
  }

  public static ItemPresentation getFieldPresentation(final PsiField psiField) {
    return new ColoredItemPresentation() {
      @Override
      public String getPresentableText() {
        return psiField.getName();
      }

      @Override
      public TextAttributesKey getTextAttributesKey() {
        if (psiField.isDeprecated()) {
          return CodeInsightColors.DEPRECATED_ATTRIBUTES;
        }
        return null;
      }

      @Override
      public String getLocationString() {
        return getJavaSymbolContainerText(psiField);
      }

      @Override
      public Image getIcon() {
        return IconDescriptorUpdaters.getIcon(psiField, Iconable.ICON_FLAG_VISIBILITY);
      }
    };
  }

  @Nullable
  private static String getJavaSymbolContainerText(final PsiElement element) {
    final String result;
    PsiElement container = PsiTreeUtil.getParentOfType(element, PsiMember.class, PsiFile.class);

    if (container instanceof PsiClass) {
      String qName = ((PsiClass)container).getQualifiedName();
      if (qName != null) {
        result = qName;
      }
      else {
        result = ((PsiClass)container).getName();
      }
    }
    else if (container instanceof PsiJavaFile) {
      result = ((PsiJavaFile)container).getPackageName();
    }
    else {//TODO: local classes
      result = null;
    }
    if (result != null) {
      return PsiBundle.message("aux.context.display", result);
    }
    return null;
  }
}
