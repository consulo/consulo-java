/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.jam;

import javax.annotation.Nonnull;

import com.intellij.jam.model.util.JamCommonUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementRef;

/**
 * @author peter
 */
public class JamClassAttributeElement extends JamAttributeElement<PsiClass> {

  public JamClassAttributeElement(@Nonnull PsiElementRef<PsiAnnotation> parent, String attributeName) {
    super(attributeName, parent);
  }

  public JamClassAttributeElement(PsiAnnotationMemberValue exactValue) {
    super(exactValue);
  }

  public String getStringValue() {
    final PsiClass value = getValue();
    if (value != null) return value.getQualifiedName();
    final PsiAnnotationMemberValue psi = getPsiElement();
    if (psi == null) return null;

    final String text = psi.getText();
    if (text != null && text.endsWith(".class")) return text.substring(0, text.length() - ".class".length());
    return null;
  }

  public PsiClass getValue() {
    return JamCommonUtil.getPsiClass(getPsiElement());
  }

}
