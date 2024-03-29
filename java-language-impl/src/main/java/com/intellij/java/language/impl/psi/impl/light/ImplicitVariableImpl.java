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

import com.intellij.java.language.psi.*;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;

/**
 * @author dsl
 */
public abstract class ImplicitVariableImpl extends LightVariableBase implements ImplicitVariable {

  public ImplicitVariableImpl(PsiManager manager, PsiIdentifier nameIdentifier, PsiType type, boolean writable, PsiElement scope) {
    super(manager, nameIdentifier, type, writable, scope);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitImplicitVariable(this);
    } else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "Implicit variable:" + getName();
  }

  @Override
  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  @Nonnull
  public SearchScope getUseScope() {
    return new LocalSearchScope(getDeclarationScope());
  }
}
