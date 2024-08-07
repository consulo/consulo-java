/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.java.language.psi.*;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;

abstract class LightClassReferenceBase extends LightElement implements PsiJavaCodeReferenceElement {

  protected final String myText;

  private LightReferenceParameterList myParameterList;

  protected LightClassReferenceBase(@Nonnull PsiManager manager, @Nonnull String text) {
    super(manager, JavaLanguage.INSTANCE);
    myText = text;
  }

  @Override
  @Nonnull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    final JavaResolveResult result = advancedResolve(incompleteCode);
    if (result != JavaResolveResult.EMPTY) {
      return new JavaResolveResult[]{result};
    }
    return JavaResolveResult.EMPTY_ARRAY;
  }

  @Override
  public void processVariants(@Nonnull PsiScopeProcessor processor) {
    throw new RuntimeException("Variants are not available for light references");
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return null;
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    LightReferenceParameterList parameterList = myParameterList;
    if (parameterList == null) {
      myParameterList = parameterList = new LightReferenceParameterList(myManager, PsiTypeElement.EMPTY_ARRAY);
    }
    return parameterList;
  }

  @Override
  public String getQualifiedName() {
    PsiClass psiClass = (PsiClass) resolve();
    if (psiClass != null) {
      return psiClass.getQualifiedName();
    }
    return null;
  }

  @Override
  public String getText() {
    return myText;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  @Nonnull
  public String getCanonicalText() {
    String name = getQualifiedName();
    if (name == null) {
      return "";
    }
    PsiType[] types = getTypeParameters();
    if (types.length == 0) {
      return name;
    }

    StringBuilder buf = new StringBuilder();
    buf.append(name);
    buf.append('<');
    for (int i = 0; i < types.length; i++) {
      if (i > 0) {
        buf.append(',');
      }
      buf.append(types[i].getCanonicalText());
    }
    buf.append('>');

    return buf.toString();
  }

  @Override
  public PsiElement handleElementRename(@Nonnull String newElementName) throws IncorrectOperationException {
    //TODO?
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    //TODO?
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitReferenceElement(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + getText();
  }

  @Override
  public boolean isReferenceTo(@Nonnull PsiElement element) {
    return element instanceof PsiClass && getManager().areElementsEquivalent(resolve(), element);
  }

  @Override
  @Nonnull
  public Object[] getVariants() {
    throw new RuntimeException("Variants are not available for light references");
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Nonnull
  @Override
  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Nonnull
  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  @Nonnull
  public PsiType[] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  @Override
  public PsiElement getQualifier() {
    return null;
  }

  @Override
  public boolean isQualified() {
    return false;
  }
}
