/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Maxim.Medvedev
 */
public class LightFieldBuilder extends LightVariableBuilder<LightFieldBuilder> implements PsiField {
  PsiClass myContainingClass = null;
  PsiExpression myInitializer = null;
  private PsiDocComment myDocComment = null;
  private boolean myIsDeprecated = false;

  public LightFieldBuilder(@Nonnull String name, @Nonnull String type, @Nonnull PsiElement navigationElement) {
    super(name, JavaPsiFacade.getElementFactory(navigationElement.getProject()).createTypeFromText(type, navigationElement),
          navigationElement);
  }

  public LightFieldBuilder(@Nonnull String name, @Nonnull PsiType type, @Nonnull PsiElement navigationElement) {
    super(name, type, navigationElement);
  }

  public LightFieldBuilder(PsiManager manager, @Nonnull String name, @Nonnull PsiType type) {
    super(manager, name, type, JavaLanguage.INSTANCE);
  }

  public LightFieldBuilder setContainingClass(PsiClass psiClass) {
    myContainingClass = psiClass;
    return this;
  }

  @Override
  public void setInitializer(@javax.annotation.Nullable PsiExpression initializer) throws IncorrectOperationException {
    myInitializer = initializer;
  }

  @Override
  public PsiExpression getInitializer() {
    return myInitializer;
  }

  @Override
  public PsiDocComment getDocComment() {
    return myDocComment;
  }

  public LightFieldBuilder setDocComment(PsiDocComment docComment) {
    myDocComment = docComment;
    return this;
  }

  @Override
  public boolean isDeprecated() {
    return myIsDeprecated;
  }

  public LightFieldBuilder setIsDeprecated(boolean isDeprecated) {
    myIsDeprecated = isDeprecated;
    return this;
  }

  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }
}