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
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiReferenceParameterList;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import consulo.language.impl.psi.LightElement;

import jakarta.annotation.Nonnull;

/**
 * @author dsl
 */
public class LightReferenceParameterList extends LightElement implements PsiReferenceParameterList {
  private final PsiTypeElement[] myTypeElements;
  private final String myText;

  public LightReferenceParameterList(PsiManager manager, PsiTypeElement[] referenceElements) {
    super(manager, JavaLanguage.INSTANCE);
    myTypeElements = referenceElements;
    myText = calculateText();
  }

  private String calculateText() {
    if (myTypeElements.length == 0) return "";
    final StringBuilder buffer = new StringBuilder();
    buffer.append("<");
    for (int i = 0; i < myTypeElements.length; i++) {
      PsiTypeElement type = myTypeElements[i];
      if (i > 0) {
        buffer.append(",");
      }
      buffer.append(type.getText());
    }
    buffer.append(">");
    return buffer.toString();
  }

  public String toString() {
    return "PsiReferenceParameterList";
  }

  @Override
  public String getText() {
    return myText;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitReferenceParameterList(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement copy() {
    final PsiTypeElement[] elements = new PsiTypeElement[myTypeElements.length];
    for (int i = 0; i < myTypeElements.length; i++) {
      PsiTypeElement typeElement = myTypeElements[i];
      elements[i] = (PsiTypeElement) typeElement.copy();
    }
    return new LightReferenceParameterList(myManager, elements);
  }

  @Override
  @Nonnull
  public PsiTypeElement[] getTypeParameterElements() {
    return myTypeElements;
  }

  @Override
  @Nonnull
  public PsiType[] getTypeArguments() {
    return PsiImplUtil.typesByTypeElements(myTypeElements);
  }
}
