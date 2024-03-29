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
package com.intellij.java.impl.usageView;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.LangBundle;
import consulo.language.psi.ElementDescriptionLocation;
import consulo.language.psi.ElementDescriptionProvider;
import consulo.language.psi.PsiElement;
import consulo.usage.UsageViewBundle;
import consulo.usage.UsageViewLongNameLocation;
import consulo.usage.UsageViewShortNameLocation;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class JavaUsageViewDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(@Nonnull final PsiElement element, @Nonnull final ElementDescriptionLocation location) {
    if (location instanceof UsageViewShortNameLocation) {
      if (element instanceof PsiThrowStatement) {
        return UsageViewBundle.message("usage.target.exception");
      }
    }

    if (location instanceof UsageViewLongNameLocation) {
      if (element instanceof PsiJavaPackage) {
        return ((PsiJavaPackage) element).getQualifiedName();
      } else if (element instanceof PsiClass) {
        if (element instanceof PsiAnonymousClass) {
          return LangBundle.message("java.terms.anonymous.class");
        } else {
          String ret = ((PsiClass) element).getQualifiedName(); // It happens for local classes
          if (ret == null) {
            ret = ((PsiClass) element).getName();
          }
          return ret;
        }
      } else if (element instanceof PsiVariable) {
        return ((PsiVariable) element).getName();
      } else if (element instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod) element;
        return PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY,
            PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS, PsiFormatUtil.SHOW_TYPE);
      }
    }

    return null;
  }
}
