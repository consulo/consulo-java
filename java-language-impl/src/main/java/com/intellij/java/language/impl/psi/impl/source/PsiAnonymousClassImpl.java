/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiClassStub;
import com.intellij.java.language.impl.psi.impl.source.tree.ChildRole;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SoftReference;

import jakarta.annotation.Nonnull;

public class PsiAnonymousClassImpl extends PsiClassImpl implements PsiAnonymousClass {
  private SoftReference<PsiClassType> myCachedBaseType;

  public PsiAnonymousClassImpl(final PsiClassStub stub) {
    super(stub, JavaStubElementTypes.ANONYMOUS_CLASS);
  }

  public PsiAnonymousClassImpl(final ASTNode node) {
    super(node);
  }

  @Override
  protected Object clone() {
    PsiAnonymousClassImpl clone = (PsiAnonymousClassImpl) super.clone();
    clone.myCachedBaseType = null;
    return clone;
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedBaseType = null;
  }

  @Override
  public PsiExpressionList getArgumentList() {
    return (PsiExpressionList) getNode().findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
  }

  @Override
  @Nonnull
  public PsiJavaCodeReferenceElement getBaseClassReference() {
    final PsiElement baseRef = getFirstChild();
    assert baseRef instanceof PsiJavaCodeReferenceElement : getText();
    return (PsiJavaCodeReferenceElement) baseRef;
  }

  @Override
  @Nonnull
  public PsiClassType getBaseClassType() {
    final PsiClassStub stub = getGreenStub();
    if (stub == null) {
      myCachedBaseType = null;
      return getTypeByTree();
    }

    PsiClassType type = SoftReference.dereference(myCachedBaseType);
    if (type != null) {
      return type;
    }

    if (!isInQualifiedNew() && !isDiamond(stub)) {
      final String refText = stub.getBaseClassReferenceText();
      assert refText != null : stub;
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();

      final PsiElement context = calcBasesResolveContext(PsiNameHelper.getShortClassName(refText), getExtendsList());
      try {
        final PsiJavaCodeReferenceElement ref = factory.createReferenceFromText(refText, context);
        ((PsiJavaCodeReferenceElementImpl) ref).setKindWhenDummy(PsiJavaCodeReferenceElementImpl.Kind.CLASS_NAME_KIND);
        type = factory.createType(ref);
      } catch (IncorrectOperationException e) {
        type = PsiType.getJavaLangObject(getManager(), getResolveScope());
      }

      myCachedBaseType = new SoftReference<PsiClassType>(type);
      return type;
    } else {
      return getTypeByTree();
    }
  }

  private boolean isDiamond(PsiClassStub stub) {
    if (PsiUtil.isLanguageLevel9OrHigher(this)) {
      final String referenceText = stub.getBaseClassReferenceText();
      if (referenceText != null && referenceText.endsWith(">")) {
        return StringUtil.trimEnd(referenceText, ">").trim().endsWith("<");
      }
    }
    return false;
  }

  private PsiClassType getTypeByTree() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(getBaseClassReference());
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public String getQualifiedName() {
    return null;
  }

  @Override
  public PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    return name.equals(PsiModifier.FINAL);
  }

  @Override
  public PsiReferenceList getExtendsList() {
    return null;
  }

  @Override
  public PsiReferenceList getImplementsList() {
    return null;
  }

  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  @Override
  public boolean isEnum() {
    return false;
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  public PsiElement getOriginalElement() {
    return this;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitAnonymousClass(this);
    } else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiAnonymousClass";
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state, PsiElement lastParent, @Nonnull PsiElement place) {
    if (lastParent instanceof PsiExpressionList) {
      return true;
    }

    if (lastParent instanceof PsiJavaCodeReferenceElement/* IMPORTANT: do not call getBaseClassReference() for lastParent == null and lastParent which is not under our node - loads tree!*/ &&
        lastParent.getParent() == this && lastParent == getBaseClassReference()) {
      return true;
    }
    return super.processDeclarations(processor, state, lastParent, place);
  }

  @Override
  public boolean isInQualifiedNew() {
    final PsiClassStub stub = getGreenStub();
    if (stub != null) {
      return stub.isAnonymousInQualifiedNew();
    }

    final PsiElement parent = getParent();
    return parent instanceof PsiNewExpression && ((PsiNewExpression) parent).getQualifier() != null;
  }
}