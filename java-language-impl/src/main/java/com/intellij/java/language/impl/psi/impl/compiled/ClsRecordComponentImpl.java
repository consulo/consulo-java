// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiRecordComponentStub;
import com.intellij.java.language.psi.*;
import consulo.content.scope.SearchScope;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.stub.StubElement;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.lazy.LazyValue;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class ClsRecordComponentImpl extends ClsRepositoryPsiElement<PsiRecordComponentStub> implements PsiRecordComponent {
  private final Supplier<PsiTypeElement> myType;

  public ClsRecordComponentImpl(PsiRecordComponentStub stub) {
    super(stub);
    myType = LazyValue.atomicNotNull(() -> new ClsTypeElementImpl(this, getStub().getType()));
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public String getName() {
    return getStub().getName();
  }

  @Override
  public PsiElement setName(String name) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public PsiTypeElement getTypeElement() {
    return myType.get();
  }

  @Override
  public PsiType getType() {
    return getTypeElement().getType();
  }

  @Override
  public PsiModifierList getModifierList() {
    final StubElement<PsiModifierList> child = getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST);
    assert child != null;
    return child.getPsi();
  }

  @Override
  public boolean hasModifierProperty(String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Override
  public Object computeConstantValue() {
    return null;
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  @Override
  public void appendMirrorText(int indentLevel, StringBuilder buffer) {
    PsiAnnotation[] annotations = getModifierList().getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      appendText(annotation, indentLevel, buffer);
      buffer.append(' ');
    }
    appendText(getTypeElement(), indentLevel, buffer, " ");
    buffer.append(getName());
  }

  @Override
  public void setMirror(TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);

    PsiParameter mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
    setMirror(getModifierList(), mirror.getModifierList());
    setMirror(getTypeElement(), mirror.getTypeElement());
  }

  @Override
  public void accept(PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitRecordComponent(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean isVarArgs() {
    return getStub().isVararg();
  }

  @Override
  public SearchScope getUseScope() {
    return new LocalSearchScope(getParent());
  }

  @Override
  public PsiElement getNavigationElement() {
    PsiClass clsClass = getContainingClass();
    if (clsClass != null) {
      PsiClass psiClass = ObjectUtil.tryCast(clsClass.getNavigationElement(), PsiClass.class);
      if (psiClass != null && psiClass != clsClass) {
        PsiRecordComponent[] clsComponents = clsClass.getRecordComponents();
        int index = ArrayUtil.indexOf(clsComponents, this);
        if (index >= 0) {
          PsiRecordComponent[] psiComponents = psiClass.getRecordComponents();
          if (psiComponents.length == clsComponents.length) {
            return psiComponents[index];
          }
        }
      }
    }
    return this;
  }

  @Override
  public String toString() {
    return "PsiRecordComponent:" + getName();
  }

  @Override
  @Nullable
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiRecordHeader ? ((PsiRecordHeader) parent).getContainingClass() : null;
  }
}
