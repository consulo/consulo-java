/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import consulo.content.scope.SearchScope;
import consulo.language.Language;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author ven
 */
public class LightMethod extends LightElement implements PsiMethod {
  protected final PsiMethod myMethod;
  protected final PsiClass myContainingClass;

  public LightMethod(@jakarta.annotation.Nonnull PsiManager manager, @jakarta.annotation.Nonnull PsiMethod method, @jakarta.annotation.Nonnull PsiClass containingClass) {
    this(manager, method, containingClass, JavaLanguage.INSTANCE);
  }

  public LightMethod(@jakarta.annotation.Nonnull PsiManager manager,
                     @jakarta.annotation.Nonnull PsiMethod method,
                     @jakarta.annotation.Nonnull PsiClass containingClass,
                     @jakarta.annotation.Nonnull Language language) {
    super(manager, language);
    myMethod = method;
    myContainingClass = containingClass;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProvider.getItemPresentation(this);
  }

  @Override
  public boolean hasTypeParameters() {
    return myMethod.hasTypeParameters();
  }

  @Override
  @jakarta.annotation.Nonnull
  public PsiTypeParameter[] getTypeParameters() {
    return myMethod.getTypeParameters();
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return myMethod.getTypeParameterList();
  }

  @Override
  public PsiDocComment getDocComment() {
    return myMethod.getDocComment();
  }

  @Override
  public boolean isDeprecated() {
    return myMethod.isDeprecated();
  }

  @Override
  public PsiElement setName(@jakarta.annotation.Nonnull String name) throws IncorrectOperationException {
    return myMethod.setName(name);
  }

  @Override
  @Nonnull
  public String getName() {
    return myMethod.getName();
  }

  @Override
  @jakarta.annotation.Nonnull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return myMethod.getHierarchicalMethodSignature();
  }

  @Override
  public boolean hasModifierProperty(@jakarta.annotation.Nonnull String name) {
    return myMethod.hasModifierProperty(name);
  }

  @Override
  @Nonnull
  public PsiModifierList getModifierList() {
    return myMethod.getModifierList();
  }

  @Override
  public PsiType getReturnType() {
    return myMethod.getReturnType();
  }

  @Override
  public PsiTypeElement getReturnTypeElement() {
    return myMethod.getReturnTypeElement();
  }

  @Override
  @jakarta.annotation.Nonnull
  public PsiParameterList getParameterList() {
    return myMethod.getParameterList();
  }

  @Override
  @jakarta.annotation.Nonnull
  public PsiReferenceList getThrowsList() {
    return myMethod.getThrowsList();
  }

  @Override
  public PsiCodeBlock getBody() {
    return myMethod.getBody();
  }

  @Override
  public boolean isConstructor() {
    return myMethod.isConstructor();
  }

  @Override
  public boolean isVarArgs() {
    return myMethod.isVarArgs();
  }

  @Override
  @jakarta.annotation.Nonnull
  public MethodSignature getSignature(@jakarta.annotation.Nonnull PsiSubstitutor substitutor) {
    return myMethod.getSignature(substitutor);
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return myMethod.getNameIdentifier();
  }

  @Override
  @jakarta.annotation.Nonnull
  public PsiMethod[] findSuperMethods() {
    return myMethod.findSuperMethods();
  }

  @Override
  @jakarta.annotation.Nonnull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return myMethod.findSuperMethods(checkAccess);
  }

  @Override
  @jakarta.annotation.Nonnull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return myMethod.findSuperMethods(parentClass);
  }

  @Override
  @jakarta.annotation.Nonnull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return myMethod.findSuperMethodSignaturesIncludingStatic(checkAccess);
  }

  @Override
  @SuppressWarnings("deprecation")
  public PsiMethod findDeepestSuperMethod() {
    return myMethod.findDeepestSuperMethod();
  }

  @Override
  @jakarta.annotation.Nonnull
  public PsiMethod[] findDeepestSuperMethods() {
    return myMethod.findDeepestSuperMethods();
  }

  @Override
  public String getText() {
    return myMethod.getText();
  }

  @Override
  public void accept(@jakarta.annotation.Nonnull PsiElementVisitor visitor) {
    myMethod.accept(visitor);
  }

  @Override
  public PsiElement copy() {
    return new LightMethod(myManager, (PsiMethod) myMethod.copy(), myContainingClass);
  }

  @Override
  public boolean isValid() {
    return myContainingClass.isValid();
  }

  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @Override
  public PsiFile getContainingFile() {
    return myContainingClass.getContainingFile();
  }

  public String toString() {
    return "PsiMethod:" + getName();
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @Override
  @jakarta.annotation.Nonnull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Override
  public PsiElement getContext() {
    return getContainingClass();
  }
}
