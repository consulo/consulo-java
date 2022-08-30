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
package com.intellij.psi.impl.light;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 */
public class LightTypeElement extends LightElement implements PsiTypeElement {
  private final PsiType myType;

  public LightTypeElement(PsiManager manager, PsiType type) {
    super(manager, JavaLanguage.INSTANCE);
    type = PsiUtil.convertAnonymousToBaseType(type);
    myType = type;
  }

  public String toString() {
    return "PsiTypeElement:" + getText();
  }

  @Override
  public String getText() {
    return myType.getPresentableText();
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement copy() {
    return new LightTypeElement(myManager, myType);
  }

  @Override
  @Nonnull
  public PsiType getType() {
    return myType;
  }

  @Override
  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    return null;
  }

  @Override
  public boolean isValid() {
    return myType.isValid();
  }

  @Override
  @Nonnull
  public PsiAnnotation[] getAnnotations() {
    return myType.getAnnotations();
  }

  @Override
  public PsiAnnotation findAnnotation(@Nonnull @NonNls String qualifiedName) {
    return myType.findAnnotation(qualifiedName);
  }

  @Override
  @Nonnull
  public PsiAnnotation addAnnotation(@Nonnull @NonNls String qualifiedName) {
    throw new IncorrectOperationException();
  }
  
  @Override
  @Nonnull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

}
