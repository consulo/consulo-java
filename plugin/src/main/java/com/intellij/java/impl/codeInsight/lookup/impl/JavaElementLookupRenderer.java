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
package com.intellij.java.impl.codeInsight.lookup.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.completion.lookup.DefaultLookupItemRenderer;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.language.editor.completion.lookup.LookupItem;
import consulo.language.editor.completion.lookup.ElementLookupRenderer;
import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.impl.psi.impl.beanProperties.BeanPropertyElement;
import com.intellij.java.language.psi.PsiDocCommentOwner;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaElementLookupRenderer implements ElementLookupRenderer {
  @Override
  public boolean handlesItem(final Object element) {
    return element instanceof BeanPropertyElement;
  }

  @Override
  public void renderElement(final LookupItem item, final Object element, final LookupElementPresentation presentation) {
    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(item, presentation.isReal()));

    presentation.setItemText(PsiUtilCore.getName((PsiElement) element));
    presentation.setStrikeout(isToStrikeout(item));

    presentation.setTailText((String) item.getAttribute(LookupItem.TAIL_TEXT_ATTR), item.getAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR) != null);

    PsiType type = ((BeanPropertyElement) element).getPropertyType();
    presentation.setTypeText(type == null ? null : type.getPresentableText());
  }

  public static boolean isToStrikeout(LookupElement item) {
    final List<PsiMethod> allMethods = JavaCompletionUtil.getAllMethods(item);
    if (allMethods != null) {
      for (PsiMethod method : allMethods) {
        if (!method.isValid()) { //?
          return false;
        }
        if (!isDeprecated(method)) {
          return false;
        }
      }
      return true;
    }
    return isDeprecated(item.getPsiElement());
  }

  private static boolean isDeprecated(@Nullable PsiElement element) {
    return element instanceof PsiDocCommentOwner && ((PsiDocCommentOwner) element).isDeprecated();
  }
}
