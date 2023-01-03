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
package com.intellij.java.impl.codeInsight.highlighting;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.LangBundle;
import consulo.language.editor.highlight.HighlightUsagesDescriptionLocation;
import consulo.language.psi.ElementDescriptionLocation;
import consulo.language.psi.ElementDescriptionProvider;
import consulo.language.psi.PsiElement;

import javax.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaHighlightUsagesDescriptionProvider implements ElementDescriptionProvider {
  @Override
  public String getElementDescription(@Nonnull final PsiElement element, @Nonnull final ElementDescriptionLocation location) {
    if (!(location instanceof HighlightUsagesDescriptionLocation)) return null;

    String elementName = null;
    if (element instanceof PsiClass) {
      elementName = ((PsiClass) element).getQualifiedName();
      if (elementName == null) {
        elementName = ((PsiClass) element).getName();
      }
      elementName = (((PsiClass) element).isInterface() ?
          LangBundle.message("java.terms.interface") :
          LangBundle.message("java.terms.class")) + " " + elementName;
    } else if (element instanceof PsiMethod) {
      elementName = PsiFormatUtil.formatMethod((PsiMethod) element,
          PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS |
              PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
          PsiFormatUtilBase.SHOW_TYPE);
      elementName = LangBundle.message("java.terms.method") + " " + elementName;
    } else if (element instanceof PsiVariable) {
      elementName = PsiFormatUtil.formatVariable((PsiVariable) element,
          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS,
          PsiSubstitutor.EMPTY);
      if (element instanceof PsiField) {
        elementName = LangBundle.message("java.terms.field") + " " + elementName;
      } else if (element instanceof PsiParameter) {
        elementName = LangBundle.message("java.terms.parameter") + " " + elementName;
      } else {
        elementName = LangBundle.message("java.terms.variable") + " " + elementName;
      }
    } else if (element instanceof PsiJavaPackage) {
      elementName = ((PsiJavaPackage) element).getQualifiedName();
      elementName = LangBundle.message("java.terms.package") + " " + elementName;
    }
    return elementName;
  }
}
