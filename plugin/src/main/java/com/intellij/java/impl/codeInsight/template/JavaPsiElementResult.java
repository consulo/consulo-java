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
package com.intellij.java.impl.codeInsight.template;

import com.intellij.java.language.impl.codeInsight.template.JavaTemplateUtilImpl;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiVariable;
import consulo.document.Document;
import consulo.language.editor.template.PsiElementResult;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

/**
 * @author yole
 */
public class JavaPsiElementResult extends PsiElementResult {
  public JavaPsiElementResult(PsiElement element) {
    super(element);
  }

  public String toString() {
    PsiElement element = getElement();
    if (element != null) {
      if (element instanceof PsiVariable) {
        return ((PsiVariable) element).getName();
      } else if (element instanceof PsiMethod) {
        return ((PsiMethod) element).getName() + "()";
      } else if (element instanceof PsiClass) {
        PsiIdentifier identifier = ((PsiClass) element).getNameIdentifier();
        if (identifier == null) return "";
        return identifier.getText();
      }
    }
    return super.toString();
  }

  @Override
  public void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
    JavaTemplateUtilImpl.updateTypeBindings(getElement(), psiFile, document, segmentStart, segmentEnd);
  }
}
