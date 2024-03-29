// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.javadoc.PsiDocTagValue;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.impl.ast.Factory;
import consulo.language.impl.ast.LeafElement;
import consulo.language.impl.ast.SharedImplUtil;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.CharTable;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PsiDocParamRef extends CompositePsiElement implements PsiDocTagValue {
  public PsiDocParamRef() {
    super(JavaDocElementType.DOC_PARAMETER_REF);
  }

  @Override
  @RequiredReadAction
  public PsiReference getReference() {
    final PsiDocComment comment = PsiTreeUtil.getParentOfType(this, PsiDocComment.class);
    if (comment == null) return null;
    final PsiJavaDocumentedElement owner = comment.getOwner();
    if (!(owner instanceof PsiMethod) &&
      !(owner instanceof PsiClass)) return null;
    ASTNode valueToken = getValueToken();
    if (valueToken == null) return null;
    final String name = valueToken.getText();
    boolean isTypeParamRef = isTypeParamRef();
    PsiElement target = ContainerUtil.find(getAllParameters(comment),
                                           param -> isTypeParamRef == param instanceof PsiTypeParameter && name.equals(param.getName()));

    TextRange range = TextRange.from(valueToken.getPsi().getStartOffsetInParent(), valueToken.getTextLength());
    return new PsiReferenceBase<PsiElement>(this, range) {
      @RequiredReadAction
      @Override
      public PsiElement resolve() {
        return target;
      }

      @Override
      public PsiElement handleElementRename(@Nonnull String newElementName) {
        final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(getNode());
        LeafElement newElement =
          Factory.createSingleLeafElement(JavaDocTokenType.DOC_TAG_VALUE_TOKEN, newElementName, charTableByTree, getManager());
        replaceChild(valueToken, newElement);
        return PsiDocParamRef.this;
      }

      @Override
      public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
        if (isReferenceTo(element)) return PsiDocParamRef.this;
        if (!(element instanceof PsiParameter)) {
          throw new IncorrectOperationException("Unsupported operation");
        }
        return handleElementRename(((PsiParameter)element).getName());
      }

      @Override
      public boolean isReferenceTo(@Nonnull PsiElement element) {
        if (!(element instanceof PsiNamedElement)) return false;
        PsiNamedElement namedElement = (PsiNamedElement)element;
        if (!getCanonicalText().equals(namedElement.getName())) return false;
        return getManager().areElementsEquivalent(resolve(), element);
      }
    };
  }

  @Nonnull
  @RequiredReadAction
  public static List<PsiNamedElement> getAllParameters(@Nonnull PsiDocComment comment) {
    List<PsiNamedElement> allParams = new ArrayList<>();
    PsiJavaDocumentedElement owner = comment.getOwner();
    if (owner instanceof PsiMethod) {
      Collections.addAll(allParams, ((PsiMethod)owner).getParameterList().getParameters());
    }
    if (owner instanceof PsiMethod || owner instanceof PsiClass) {
      PsiTypeParameterList tpl = ((PsiTypeParameterListOwner)owner).getTypeParameterList();
      if (tpl != null) {
        Collections.addAll(allParams, tpl.getTypeParameters());
      }
    }
    if (owner instanceof PsiClass && ((PsiClass)owner).isRecord()) {
      Collections.addAll(allParams, ((PsiClass)owner).getRecordComponents());
    }
    return allParams;
  }

  public boolean isTypeParamRef() {
    return PsiUtilCore.getElementType(getFirstChild()) == JavaDocTokenType.DOC_TAG_VALUE_LT;
  }

  @Nullable
  public ASTNode getValueToken() {
    return findChildByType(JavaDocTokenType.DOC_TAG_VALUE_TOKEN);
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
}
