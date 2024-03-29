/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiJavaModuleStub;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.util.collection.JBIterable;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static consulo.language.psi.SyntaxTraverser.psiTraverser;

public class PsiJavaModuleImpl extends JavaStubPsiElement<PsiJavaModuleStub> implements PsiJavaModule {
  public PsiJavaModuleImpl(@Nonnull PsiJavaModuleStub stub) {
    super(stub, JavaStubElementTypes.MODULE);
  }

  public PsiJavaModuleImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Nonnull
  @Override
  public Iterable<PsiRequiresStatement> getRequires() {
    PsiJavaModuleStub stub = getGreenStub();
    if (stub != null) {
      return JBIterable.of(stub.getChildrenByType(JavaElementType.REQUIRES_STATEMENT, PsiRequiresStatement.EMPTY_ARRAY));
    } else {
      return psiTraverser().children(this).filter(PsiRequiresStatement.class);
    }
  }

  @Nonnull
  @Override
  public Iterable<PsiPackageAccessibilityStatement> getExports() {
    PsiJavaModuleStub stub = getGreenStub();
    if (stub != null) {
      return JBIterable.of(stub.getChildrenByType(JavaElementType.EXPORTS_STATEMENT, PsiPackageAccessibilityStatement.EMPTY_ARRAY));
    } else {
      return psiTraverser().children(this).filter(PsiPackageAccessibilityStatement.class).filter(statement -> statement.getRole() == PsiPackageAccessibilityStatement.Role.EXPORTS);
    }
  }

  @Nonnull
  @Override
  public Iterable<PsiPackageAccessibilityStatement> getOpens() {
    PsiJavaModuleStub stub = getGreenStub();
    if (stub != null) {
      return JBIterable.of(stub.getChildrenByType(JavaElementType.OPENS_STATEMENT, PsiPackageAccessibilityStatement.EMPTY_ARRAY));
    } else {
      return psiTraverser().children(this).filter(PsiPackageAccessibilityStatement.class).filter(statement -> statement.getRole() == PsiPackageAccessibilityStatement.Role.OPENS);
    }
  }

  @Nonnull
  @Override
  public Iterable<PsiUsesStatement> getUses() {
    return psiTraverser().children(this).filter(PsiUsesStatement.class);
  }

  @Nonnull
  @Override
  public Iterable<PsiProvidesStatement> getProvides() {
    return psiTraverser().children(this).filter(PsiProvidesStatement.class);
  }

  @Nonnull
  @Override
  public PsiJavaModuleReferenceElement getNameIdentifier() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiJavaModuleReferenceElement.class);
  }

  @Nonnull
  @Override
  public String getName() {
    PsiJavaModuleStub stub = getGreenStub();
    if (stub != null) {
      return stub.getName();
    } else {
      return getNameIdentifier().getReferenceText();
    }
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(getProject());
    PsiJavaModuleReferenceElement newName = factory.createModuleFromText("module " + name + " {}").getNameIdentifier();
    getNameIdentifier().replace(newName);
    return this;
  }

  @Override
  public PsiModifierList getModifierList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Nullable
  @Override
  public PsiDocComment getDocComment() {
    return PsiTreeUtil.getChildOfType(this, PsiDocComment.class);
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProvider.getItemPresentation(this);
  }

  @Override
  public int getTextOffset() {
    return getNameIdentifier().getTextOffset();
  }

  @Nonnull
  @Override
  public PsiElement getNavigationElement() {
    return getNameIdentifier();
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitModule(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiJavaModule:" + getName();
  }
}