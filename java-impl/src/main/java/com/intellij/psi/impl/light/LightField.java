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
package com.intellij.psi.impl.light;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;

public class LightField extends LightElement implements PsiField {
  private final PsiField myField;
  private final PsiClass myContainingClass;

  public LightField(@Nonnull final PsiManager manager, @Nonnull final PsiField field, @Nonnull final PsiClass containingClass) {
    super(manager, JavaLanguage.INSTANCE);
    myField = field;
    myContainingClass = containingClass;
  }

  @Override
  public void setInitializer(@Nullable final PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not supported");
  }

  @Nonnull
  @Override
  public SearchScope getUseScope() {
    return myField.getUseScope();
  }

  @Override
  public String getName() {
    return myField.getName();
  }

  @Nonnull
  @Override
  public PsiIdentifier getNameIdentifier() {
    return myField.getNameIdentifier();
  }

  @Override
  public PsiDocComment getDocComment() {
    return myField.getDocComment();
  }

  @Override
  public boolean isDeprecated() {
    return myField.isDeprecated();
  }

  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @Nonnull
  @Override
  public PsiType getType() {
    return myField.getType();
  }

  @Override
  public PsiTypeElement getTypeElement() {
    return myField.getTypeElement();
  }

  @Override
  public PsiExpression getInitializer() {
    return myField.getInitializer();
  }

  @Override
  public boolean hasInitializer() {
    return myField.hasInitializer();
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
    throw new IncorrectOperationException("Not supported");
  }

  @Override
  public Object computeConstantValue() {
    return myField.computeConstantValue();
  }

  @Override
  public PsiElement setName(@NonNls @Nonnull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not supported");
  }

  @Override
  public PsiModifierList getModifierList() {
    return myField.getModifierList();
  }

  @Override
  public boolean hasModifierProperty(@NonNls @Nonnull final String name) {
    return myField.hasModifierProperty(name);
  }

  @Override
  public String getText() {
    return myField.getText();
  }

  @Override
  public PsiElement copy() {
    return new LightField(myManager, (PsiField)myField.copy(), myContainingClass);
  }

  @Override
  public TextRange getTextRange() {
    return new TextRange(-1, -1);
  }

  @Override
  public boolean isValid() {
    return myContainingClass.isValid();
  }

  @Override
  public String toString() {
    return "PsiField:" + getName();
  }
}
