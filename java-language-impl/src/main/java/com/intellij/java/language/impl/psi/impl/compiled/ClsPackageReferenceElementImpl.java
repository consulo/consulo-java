// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.JavaResolveResult;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.infos.CandidateInfo;
import consulo.document.util.TextRange;
import consulo.language.impl.ast.TreeElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.PsiQualifiedReferenceElement;
import com.intellij.java.language.psi.PsiReferenceParameterList;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.util.IncorrectOperationException;

import java.util.Objects;

public class ClsPackageReferenceElementImpl extends ClsElementImpl implements PsiJavaCodeReferenceElement, PsiQualifiedReferenceElement {
  private final PsiElement myParent;
  private final String myQualifiedName;

  public ClsPackageReferenceElementImpl(PsiElement parent, String canonicalText) {
    myParent = parent;
    myQualifiedName = canonicalText;
  }

  @Override
  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public String getCanonicalText() {
    return myQualifiedName;
  }

  private static class Resolver implements ResolveCache.AbstractResolver<ClsPackageReferenceElementImpl, JavaResolveResult> {
    public static final Resolver INSTANCE = new Resolver();

    @Override
    public JavaResolveResult resolve(ClsPackageReferenceElementImpl ref, boolean incompleteCode) {
      return ref.advancedResolveImpl(ref.getContainingFile());
    }
  }

  private JavaResolveResult advancedResolveImpl(PsiFile containingFile) {
    PsiElement resolve = JavaPsiFacade.getInstance(containingFile.getProject()).findPackage(myQualifiedName);
    if (resolve == null) return null;
    return new CandidateInfo(resolve, PsiSubstitutor.EMPTY);
  }

  @Override
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    JavaResolveResult result = ResolveCache.getInstance(getProject())
      .resolveWithCaching(this, Resolver.INSTANCE, false, incompleteCode);
    return result == null ? JavaResolveResult.EMPTY : result;
  }

  @Override
  public JavaResolveResult[] multiResolve(boolean incompleteCode) {
    PsiFile file = getContainingFile();
    if (file == null) {
      PsiElement root = SyntaxTraverser.psiApi().parents(this).last();
      PsiUtilCore.ensureValid(Objects.requireNonNull(root));
      throw new PsiInvalidElementAccessException(this, "parent=" + myParent + ", root=" + root + ", canonicalText=" + myQualifiedName);
    }
    JavaResolveResult result = advancedResolve(incompleteCode);
    return result == JavaResolveResult.EMPTY ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[]{result};
  }

  @Override
  public PsiElement resolve() {
    return advancedResolve(true).getElement();
  }

  @Override
  public void processVariants(PsiScopeProcessor processor) {
    throw new RuntimeException("Variants are not available for compiled references");
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return null;
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    return null;
  }

  @Override
  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public String getReferenceName() {
    return myQualifiedName;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    if (!(element instanceof PsiPackage)) return false;
    PsiPackage aPackage = (PsiPackage)element;
    return myQualifiedName.equals(aPackage.getQualifiedName()) || getManager().areElementsEquivalent(resolve(), element);
  }

  @Override
  public Object[] getVariants() {
    throw new RuntimeException("Variants are not available for references to compiled code");
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
    buffer.append(getCanonicalText());
  }

  @Override
  public void setMirror(TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.JAVA_CODE_REFERENCE);
  }

  @Override
  public void accept(PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public PsiType[] getTypeParameters() {
    return PsiType.EMPTY_ARRAY;
  }

  @Override
  public boolean isQualified() {
    return true;
  }

  @Override
  public PsiElement getQualifier() {
    return null;
  }

  @Override
  public String getText() {
    return myQualifiedName;
  }

  @Override
  public String toString() {
    return "ClsPackageReferenceElement:" + getText();
  }
}
