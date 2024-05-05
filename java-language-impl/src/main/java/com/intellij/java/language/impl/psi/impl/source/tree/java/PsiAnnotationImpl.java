/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.java.language.impl.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.java.language.impl.psi.impl.source.JavaStubPsiElement;
import com.intellij.java.language.impl.psi.impl.source.PsiClassReferenceType;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.java.language.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.meta.MetaDataService;
import consulo.language.psi.meta.PsiMetaData;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.PairFunction;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ven
 */
public class PsiAnnotationImpl extends JavaStubPsiElement<PsiAnnotationStub> implements PsiAnnotation {
  private static final PairFunction<Project, String, PsiAnnotation> ANNOTATION_CREATOR =
    (project, text) -> JavaPsiFacade.getInstance(project).getElementFactory().createAnnotationFromText(text,
                                                                                                       null);

  public PsiAnnotationImpl(final PsiAnnotationStub stub) {
    super(stub, JavaStubElementTypes.ANNOTATION);
  }

  public PsiAnnotationImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    PsiAnnotationStub stub = getStub();
    return PsiTreeUtil.getChildOfType(stub != null ? stub.getPsiElement() : this, PsiJavaCodeReferenceElement.class);
  }

  @Nullable
  private String getShortName() {
    PsiAnnotationStub stub = getStub();
    if (stub != null) {
      return getAnnotationShortName(stub.getText());
    }

    PsiJavaCodeReferenceElement nameRef = getNameReferenceElement();
    return nameRef == null ? null : nameRef.getReferenceName();
  }

  @Nonnull
  public static String getAnnotationShortName(@Nonnull String annoText) {
    int at = annoText.indexOf('@');
    int paren = annoText.indexOf('(');
    String qualified = PsiNameHelper.getQualifiedClassName(annoText.substring(at + 1, paren > 0 ? paren : annoText.length()), true);
    return StringUtil.getShortName(qualified);
  }

  @Override
  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Override
  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(final String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  @Nullable
  @Override
  public PsiAnnotationMemberValue findDeclaredAttributeDetachedValue(@Nullable String attributeName) {
    return PsiImplUtil.findDeclaredAttributeDetachedValue(this, attributeName);
  }

  @Override
  public <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(String attributeName, @Nullable T value) {
    @SuppressWarnings("unchecked") T t = (T)PsiImplUtil.setDeclaredAttributeValue(this, attributeName, value, ANNOTATION_CREATOR);
    return t;
  }

  public String toString() {
    return "PsiAnnotation";
  }

  @Override
  @Nonnull
  public PsiAnnotationParameterList getParameterList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.ANNOTATION_PARAMETER_LIST);
  }

  @Override
  @Nullable
  public String getQualifiedName() {
    final PsiJavaCodeReferenceElement nameRef = getNameReferenceElement();
    if (nameRef == null) {
      return null;
    }
    return nameRef.getCanonicalText();
  }

  @Override
  public final void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotation(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiMetaData getMetaData() {
    return MetaDataService.getInstance().getMeta(this);
  }

  @Override
  public boolean hasQualifiedName(@Nonnull String qualifiedName) {
    return StringUtil.getShortName(qualifiedName).equals(getShortName()) && PsiAnnotation.super.hasQualifiedName(qualifiedName);
  }

  @Nullable
  @Override
  public PsiAnnotationOwner getOwner() {
    PsiElement parent = getParent();

    if (parent instanceof PsiAnnotationOwner) {
      return (PsiAnnotationOwner)parent;
    }

    if (parent instanceof PsiNewExpression) {
      return ((PsiNewExpression)parent).getOwner(this);
    }

    if (parent instanceof PsiReferenceExpression) {
      PsiElement ctx = parent.getParent();
      if (ctx instanceof PsiMethodReferenceExpression) {
        return new PsiClassReferenceType((PsiJavaCodeReferenceElement)parent, null);
      }
    }
    else if (parent instanceof PsiJavaCodeReferenceElement) {
      PsiElement ctx = PsiTreeUtil.skipParentsOfType(parent, PsiJavaCodeReferenceElement.class);
      if (ctx instanceof PsiReferenceList || ctx instanceof PsiNewExpression || ctx instanceof PsiTypeElement || ctx instanceof PsiAnonymousClass) {
        return new PsiClassReferenceType((PsiJavaCodeReferenceElement)parent, null);
      }
    }

    PsiTypeElement typeElement = null;
    PsiElement anchor = null;
    if (parent instanceof PsiMethod) {
      typeElement = ((PsiMethod)parent).getReturnTypeElement();
      anchor = ((PsiMethod)parent).getParameterList();
    }
    else if (parent instanceof PsiField || parent instanceof PsiParameter || parent instanceof PsiLocalVariable) {
      typeElement = ((PsiVariable)parent).getTypeElement();
      anchor = ((PsiVariable)parent).getNameIdentifier();
    }
    if (typeElement != null && anchor != null) {
      return JavaSharedImplUtil.getType(typeElement, anchor, this);
    }

    return null;
  }
}
