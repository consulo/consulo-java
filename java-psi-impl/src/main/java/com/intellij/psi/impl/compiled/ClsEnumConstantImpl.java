/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.compiled;

import javax.annotation.Nonnull;

import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class ClsEnumConstantImpl extends ClsFieldImpl implements PsiEnumConstant {
  public ClsEnumConstantImpl(@Nonnull PsiFieldStub stub) {
    super(stub);
  }

  @Override
  public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
    appendText(getDocComment(), indentLevel, buffer, NEXT_LINE);
    appendText(getModifierList(), indentLevel, buffer, "");
    appendText(getNameIdentifier(), indentLevel, buffer);
  }

  @Override
  public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);

    PsiField mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
    setMirrorIfPresent(getDocComment(), mirror.getDocComment());
    setMirror(getModifierList(), mirror.getModifierList());
    setMirror(getNameIdentifier(), mirror.getNameIdentifier());
  }

  @Override
  public PsiExpressionList getArgumentList() {
    return null;
  }

  @Override
  public PsiMethod resolveMethod() {
    return null;
  }

  @Override
  @Nonnull
  public JavaResolveResult resolveMethodGenerics() {
    return JavaResolveResult.EMPTY;
  }

  @Override
  public PsiEnumConstantInitializer getInitializingClass() {
    return null;
  }

  @Nonnull
  @Override
  public PsiEnumConstantInitializer getOrCreateInitializingClass() {
    throw new IncorrectOperationException("cannot create initializing class in cls enum constant");
  }

  @Override
  public PsiMethod resolveConstructor() {
    return null;
  }

  @Override
  @Nonnull
  public PsiType getType() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(getContainingClass());
  }

  @Override
  public PsiTypeElement getTypeElement() {
    return null;
  }

  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return true;
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    return PsiModifier.PUBLIC.equals(name) || PsiModifier.STATIC.equals(name) || PsiModifier.FINAL.equals(name);
  }
}
