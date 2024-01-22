// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiLoopStatement;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.CompositePsiElement;
import jakarta.annotation.Nonnull;

abstract class PsiLoopStatementImpl extends CompositePsiElement implements PsiLoopStatement {
  protected PsiLoopStatementImpl(IElementType type) {
    super(type);
  }

  @Override
  public void deleteChildInternal(@Nonnull ASTNode child) {
    if (child == getBody()) {
      replaceChildInternal(child, (TreeElement)JavaPsiFacade.getElementFactory(getProject()).createStatementFromText("{}", null));
    }
    else {
      super.deleteChildInternal(child);
    }
  }
}
