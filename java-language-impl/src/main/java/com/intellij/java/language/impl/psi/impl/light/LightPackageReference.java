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
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.infos.CandidateInfo;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;

public class LightPackageReference extends LightElement implements PsiJavaCodeReferenceElement {
  private final String myPackageName;
  private final PsiJavaPackage myRefPackage;

  public LightPackageReference(PsiManager manager, PsiJavaPackage refPackage) {
    super(manager, JavaLanguage.INSTANCE);
    myPackageName = null;
    myRefPackage = refPackage;
  }

  public LightPackageReference(PsiManager manager, String packageName) {
    super(manager, JavaLanguage.INSTANCE);
    myPackageName = packageName;
    myRefPackage = null;
  }

  @Override
  public PsiElement resolve() {
    if (myPackageName != null) {
      return JavaPsiFacade.getInstance(myManager.getProject()).findPackage(myPackageName);
    } else {
      return myRefPackage;
    }
  }

  @Override
  @Nonnull
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    return new CandidateInfo(resolve(), PsiSubstitutor.EMPTY);
  }

  @Override
  @Nonnull
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    final JavaResolveResult result = advancedResolve(incompleteCode);
    if (result != JavaResolveResult.EMPTY) return new JavaResolveResult[]{result};
    return JavaResolveResult.EMPTY_ARRAY;
  }

  @Override
  public String getText() {
    if (myPackageName != null) {
      return myPackageName;
    } else {
      return myRefPackage.getQualifiedName();
    }
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  @Nonnull
  public String getCanonicalText() {
    return getText();
  }

  @Override
  public PsiElement copy() {
    if (myPackageName != null) {
      return new LightPackageReference(myManager, myPackageName);
    } else {
      return new LightPackageReference(myManager, myRefPackage);
    }
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
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

  public String toString() {
    return "PsiJavaCodeReferenceElement:" + getText();
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiJavaPackage)) return false;
    return getManager().areElementsEquivalent(resolve(), element);
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

  @Override
  public void processVariants(PsiScopeProcessor processor) {
    throw new RuntimeException("Variants are not available for light references");
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return null;
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  @Override
  public String getQualifiedName() {
    return getText();
  }

  @Override
  public String getReferenceName() {
    if (myPackageName != null) {
      return PsiNameHelper.getShortClassName(myPackageName);
    } else {
      return myRefPackage.getName();
    }
  }

  @Override
  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public boolean isValid() {
    return myRefPackage == null || myRefPackage.isValid();
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
