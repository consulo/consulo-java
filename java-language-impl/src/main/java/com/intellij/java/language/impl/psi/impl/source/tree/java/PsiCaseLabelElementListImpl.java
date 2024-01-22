// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.source.tree.ElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiCaseLabelElement;
import com.intellij.java.language.psi.PsiCaseLabelElementList;
import com.intellij.java.language.psi.PsiParenthesizedExpression;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.ast.ASTNode;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.function.Predicate;

public class PsiCaseLabelElementListImpl extends CompositePsiElement implements PsiCaseLabelElementList {
  private volatile PsiCaseLabelElement[] myElements;

  public PsiCaseLabelElementListImpl() {
    super(JavaElementType.CASE_LABEL_ELEMENT_LIST);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myElements = null;
  }

  @Override
  @Nonnull
  public PsiCaseLabelElement[] getElements() {
    PsiCaseLabelElement[] elements = myElements;
    if (elements == null) {
      elements = getChildrenAsPsiElements(ElementType.JAVA_CASE_LABEL_ELEMENT_BIT_SET, PsiCaseLabelElement.ARRAY_FACTORY);
      if (elements.length > 10) {
        myElements = elements;
      }
    }
    return elements;
  }

  @Override
  public int getElementCount() {
    PsiCaseLabelElement[] elements = myElements;
    if (elements != null) return elements.length;

    return countChildren(ElementType.JAVA_CASE_LABEL_ELEMENT_BIT_SET);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitCaseLabelElementList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }


  @Override
  public TreeElement addInternal(TreeElement first,
                                 ASTNode last,
                                 @Nullable ASTNode anchor,
                                 @Nullable Boolean before) {
    TreeElement firstAdded = super.addInternal(first, last, anchor, before);
    TreeElement element = first;
    while (true) {
      if (ElementType.JAVA_CASE_LABEL_ELEMENT_BIT_SET.contains(element.getElementType())) {
        JavaSourceUtil.addSeparatingComma(this, element, ElementType.JAVA_CASE_LABEL_ELEMENT_BIT_SET);
        break;
      }
      if (element == last) break;
      element = element.getTreeNext();
    }
    return firstAdded;
  }

  @Override
  public void deleteChildInternal(@Nonnull ASTNode child) {
    if (ElementType.JAVA_CASE_LABEL_ELEMENT_BIT_SET.contains(child.getElementType())) {
      JavaSourceUtil.deleteSeparatingComma(this, child);
    }

    super.deleteChildInternal(child);
  }

  @Override
  public String toString() {
    return "PsiCaseLabelElementList";
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    if (oneOfElements(place)) return true;

    for (PsiCaseLabelElement label : getElements()) {
      boolean shouldKeepGoing = label.processDeclarations(processor, state, null, place);
      if (!shouldKeepGoing) return false;
    }
    return true;
  }

  private boolean oneOfElements(@Nonnull PsiElement place) {
    return Arrays.stream(getElements())
                 .map(PsiCaseLabelElementListImpl::skipParenthesis)
                 .anyMatch(Predicate.isEqual(place));
  }

  @Nullable
  private static PsiCaseLabelElement skipParenthesis(PsiCaseLabelElement e) {
    if (!(e instanceof PsiParenthesizedExpression)) return e;

    return PsiUtil.skipParenthesizedExprDown((PsiParenthesizedExpression)e);
  }
}
