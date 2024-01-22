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
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import consulo.language.ast.ASTNode;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.JavaElementVisitor;
import consulo.language.psi.PsiElementVisitor;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import consulo.language.impl.psi.CompositePsiElement;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public class CorePsiDocTagValueImpl extends CompositePsiElement implements PsiDocTagValue {
  public CorePsiDocTagValueImpl() {
    super(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTagValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    if (child.getElementType() == JavaDocTokenType.DOC_TAG_VALUE_COMMA) {
      return ChildRole.COMMA;
    }

    return super.getChildRole(child);
  }
}
