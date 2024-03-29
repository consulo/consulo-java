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
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.impl.psi.CodeEditUtil;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;

public class PsiIdentifierImpl extends LeafPsiElement implements PsiIdentifier, PsiJavaToken {
  public PsiIdentifierImpl(CharSequence text) {
    super(Constants.IDENTIFIER, text);
  }

  @Override
  public IElementType getTokenType() {
    return JavaTokenType.IDENTIFIER;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitIdentifier(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement replace(@Nonnull PsiElement newElement) throws IncorrectOperationException {
    PsiElement result = super.replace(newElement);

    // We want to reformat method parameters on method name change as well because there is a possible situation that they are aligned
    // and method name change breaks the alignment.
    // Example:
    //     public void test(int i,
    //                      int j) {}
    // Suppose we're renaming the method to test123. We get the following if parameter list is not reformatted:
    //     public void test123(int i,
    //                     int j) {}
    PsiElement methodCandidate = result.getParent();
    if (methodCandidate instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) methodCandidate;
      CodeEditUtil.markToReformat(method.getParameterList().getNode(), true);
    }

    return result;
  }

  public String toString() {
    return "PsiIdentifier:" + getText();
  }
}
