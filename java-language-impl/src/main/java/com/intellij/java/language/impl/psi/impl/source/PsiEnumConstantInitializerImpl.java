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
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiClassStub;
import com.intellij.java.language.impl.psi.impl.light.LightClassReference;
import com.intellij.java.language.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;

public class PsiEnumConstantInitializerImpl extends PsiClassImpl implements PsiEnumConstantInitializer {
  private static final Logger LOG = Logger.getInstance(PsiEnumConstantInitializerImpl.class);
  private PsiClassType myCachedBaseType = null;

  public PsiEnumConstantInitializerImpl(final PsiClassStub stub) {
    super(stub, JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER);
  }

  public PsiEnumConstantInitializerImpl(final ASTNode node) {
    super(node);
  }

  @Override
  protected Object clone() {
    PsiEnumConstantInitializerImpl clone = (PsiEnumConstantInitializerImpl) super.clone();
    clone.myCachedBaseType = null;
    return clone;
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedBaseType = null;
  }

  @Override
  public PsiExpressionList getArgumentList() {
    PsiElement parent = getParent();
    LOG.assertTrue(parent instanceof PsiEnumConstant);
    return ((PsiCall) parent).getArgumentList();
  }

  @Override
  public boolean isInQualifiedNew() {
    return false;
  }

  @Override
  @Nonnull
  public PsiJavaCodeReferenceElement getBaseClassReference() {
    PsiClass containingClass = getBaseClass();
    return new LightClassReference(getManager(), containingClass.getName(), containingClass);
  }

  private PsiClass getBaseClass() {
    PsiElement parent = getParent();
    LOG.assertTrue(parent instanceof PsiEnumConstant);
    PsiClass containingClass = ((PsiEnumConstant) parent).getContainingClass();
    LOG.assertTrue(containingClass != null);
    return containingClass;
  }

  @Override
  @Nonnull
  public PsiEnumConstant getEnumConstant() {
    return (PsiEnumConstant) getParent();
  }

  @Override
  @Nonnull
  public PsiClassType getBaseClassType() {
    if (myCachedBaseType == null) {
      myCachedBaseType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(getBaseClass());
    }
    return myCachedBaseType;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public String getQualifiedName() {
    return null;
  }

  @Override
  public PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    return false;
  }

  @Override
  public PsiReferenceList getExtendsList() {
    return null;
  }

  @Override
  public PsiReferenceList getImplementsList() {
    return null;
  }

  @Override
  @Nonnull
  public PsiClassType[] getSuperTypes() {
    return new PsiClassType[]{getBaseClassType()};
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  @Override
  public boolean isEnum() {
    return false;
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  public PsiElement getOriginalElement() {
    return this;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitEnumConstantInitializer(this);
    } else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiAnonymousClass (PsiEnumConstantInitializerImpl)):";
  }
}