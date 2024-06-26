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
package com.intellij.java.impl.codeInsight.unwrap;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiStatement;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.util.IncorrectOperationException;

import java.util.Set;

public abstract class JavaElseUnwrapperBase extends JavaUnwrapper {
  public JavaElseUnwrapperBase(String description) {
    super(description);
  }

  @Override
  public boolean isApplicableTo(PsiElement e) {
    return (PsiUtil.isElseBlock(e) || isElseKeyword(e)) && isValidConstruct(e);
  }

  private boolean isElseKeyword(PsiElement e) {
    PsiElement p = e.getParent();
    return p instanceof PsiIfStatement && e == ((PsiIfStatement)p).getElseElement();
  }

  private boolean isValidConstruct(PsiElement e) {
    return ((PsiIfStatement)e.getParent()).getElseBranch() != null;
  }

  @Override
  public void collectElementsToIgnore(PsiElement element, Set<PsiElement> result) {
    PsiElement parent = element.getParent();

    while (parent instanceof PsiIfStatement) {
      result.add(parent);
      parent = parent.getParent();
    }
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiStatement elseBranch;

    if (isElseKeyword(element)) {
      elseBranch = ((PsiIfStatement)element.getParent()).getElseBranch();
    }
    else {
      elseBranch = (PsiStatement)element;
    }

    unwrapElseBranch(elseBranch, element.getParent(), context);
  }

  protected abstract void unwrapElseBranch(PsiStatement branch, PsiElement parent, Context context) throws IncorrectOperationException;
}