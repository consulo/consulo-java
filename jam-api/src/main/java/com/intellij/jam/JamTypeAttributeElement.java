/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.intellij.jam;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiAnnotationMemberValue;
import com.intellij.java.language.psi.PsiClassObjectAccessExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.psi.*;
import consulo.java.language.module.util.JavaClassNames;

/**
 * @author peter
 */
public class JamTypeAttributeElement extends JamAttributeElement<PsiType> {

  public JamTypeAttributeElement(@Nonnull PsiElementRef<PsiAnnotation> parent, String attributeName) {
    super(attributeName, parent);
  }

  public JamTypeAttributeElement(PsiAnnotationMemberValue exactValue) {
    super(exactValue);
  }

  public String getStringValue() {
    final PsiType value = getValue();
    return value == null ? null : value.getCanonicalText();
  }

  public PsiType getValue() {
    final PsiAnnotationMemberValue psiAnnotationMemberValue = getPsiElement();
    PsiType psiType = null;
    if (psiAnnotationMemberValue instanceof PsiClassObjectAccessExpression) {
      psiType = ((PsiClassObjectAccessExpression)psiAnnotationMemberValue).getOperand().getType();
    }
    if (psiType != null && JavaClassNames.JAVA_LANG_OBJECT.equals(psiType.getCanonicalText())) {
      return null;
    }
    return psiType;
  }

}
