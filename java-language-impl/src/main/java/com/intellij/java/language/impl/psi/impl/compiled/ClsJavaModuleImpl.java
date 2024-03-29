// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiJavaModuleStub;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.stub.StubElement;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import consulo.util.collection.JBIterable;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

import static consulo.util.collection.ContainerUtil.newArrayList;

public class ClsJavaModuleImpl extends ClsRepositoryPsiElement<PsiJavaModuleStub> implements PsiJavaModule {
  private final PsiJavaModuleReferenceElement myReference;

  public ClsJavaModuleImpl(PsiJavaModuleStub stub) {
    super(stub);
    myReference = new ClsJavaModuleReferenceElementImpl(this, stub.getName());
  }

  @Nonnull
  @Override
  public Iterable<PsiRequiresStatement> getRequires() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.REQUIRES_STATEMENT, PsiRequiresStatement.EMPTY_ARRAY));
  }

  @Nonnull
  @Override
  public Iterable<PsiPackageAccessibilityStatement> getExports() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.EXPORTS_STATEMENT, PsiPackageAccessibilityStatement.EMPTY_ARRAY));
  }

  @Nonnull
  @Override
  public Iterable<PsiPackageAccessibilityStatement> getOpens() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.OPENS_STATEMENT, PsiPackageAccessibilityStatement.EMPTY_ARRAY));
  }

  @Nonnull
  @Override
  public Iterable<PsiUsesStatement> getUses() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.USES_STATEMENT, PsiUsesStatement.EMPTY_ARRAY));
  }

  @Nonnull
  @Override
  public Iterable<PsiProvidesStatement> getProvides() {
    return JBIterable.of(getStub().getChildrenByType(JavaElementType.PROVIDES_STATEMENT, PsiProvidesStatement.EMPTY_ARRAY));
  }

  @Override
  public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
    appendText(getModifierList(), indentLevel, buffer);
    buffer.append("module ").append(getName()).append(" {\n");

    int newIndentLevel = indentLevel + getIndentSize(), start = buffer.length();
    appendChildren(getRequires(), buffer, newIndentLevel, start);
    appendChildren(getExports(), buffer, newIndentLevel, start);
    appendChildren(getOpens(), buffer, newIndentLevel, start);
    appendChildren(getUses(), buffer, newIndentLevel, start);
    appendChildren(getProvides(), buffer, newIndentLevel, start);

    StringUtil.repeatSymbol(buffer, ' ', indentLevel);
    buffer.append('}');
  }

  private static void appendChildren(Iterable<? extends PsiElement> children, StringBuilder buffer, int indentLevel, int start) {
    List<PsiElement> statements = newArrayList(children);
    if (!statements.isEmpty()) {
      if (buffer.length() > start) {
        buffer.append('\n');
      }
      for (PsiElement statement : statements) {
        appendText(statement, indentLevel, buffer);
      }
    }
  }

  @Override
  public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.MODULE);

    PsiJavaModule mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);

    setMirror(getNameIdentifier(), mirror.getNameIdentifier());
    setMirror(getModifierList(), mirror.getModifierList());

    setMirrors(getRequires(), mirror.getRequires());
    setMirrors(getExports(), mirror.getExports());
    setMirrors(getOpens(), mirror.getOpens());
    setMirrors(getUses(), mirror.getUses());
    setMirrors(getProvides(), mirror.getProvides());
  }

  private static <T extends PsiElement> void setMirrors(Iterable<T> stubs, Iterable<T> mirrors) {
    setMirrors(newArrayList(stubs), newArrayList(mirrors));
  }

  @Nonnull
  @Override
  public PsiJavaModuleReferenceElement getNameIdentifier() {
    return myReference;
  }

  @Nonnull
  @Override
  public String getName() {
    return myReference.getReferenceText();
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public PsiModifierList getModifierList() {
    StubElement<PsiModifierList> childStub = getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST);
    return childStub != null ? childStub.getPsi() : null;
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Nullable
  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProvider.getItemPresentation(this);
  }

  @Nonnull
  @Override
  public PsiElement getNavigationElement() {
    PsiElement parent = getParent();
    if (parent instanceof PsiClassOwner) {
      PsiElement file = parent.getNavigationElement();
      if (file != parent && file instanceof PsiJavaFile) {
        PsiJavaModule module = ((PsiJavaFile) file).getModuleDeclaration();
        if (module != null) {
          return module;
        }
      }
    }

    return this;
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