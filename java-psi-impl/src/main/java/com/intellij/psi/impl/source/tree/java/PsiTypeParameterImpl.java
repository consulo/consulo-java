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
package com.intellij.psi.impl.source.tree.java;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterListStub;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterStub;
import com.intellij.psi.impl.light.LightEmptyImplementsList;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class PsiTypeParameterImpl extends JavaStubPsiElement<PsiTypeParameterStub> implements PsiTypeParameter {
  private final LightEmptyImplementsList myLightEmptyImplementsList = new LightEmptyImplementsList(null) {
    @Override
    public PsiManager getManager() {
      return PsiTypeParameterImpl.this.getManager();
    }
  };

  public PsiTypeParameterImpl(final PsiTypeParameterStub stub) {
    super(stub, JavaStubElementTypes.TYPE_PARAMETER);
  }

  public PsiTypeParameterImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public String getQualifiedName() {
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
  @Nonnull
  public PsiField[] getFields() {
    return PsiField.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiMethod[] getMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  @Nonnull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  public PsiField findFieldByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  @Override
  @Nonnull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @Override
  @Nonnull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  @Nonnull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD);
  }

  @Override
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findInnerByName(this, name, checkBases);
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  public boolean hasTypeParameters() {
    return false;
  }

  // very special method!
  @Override
  public PsiElement getScope() {
    return getParent().getParent();
  }

  @Override
  public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Override
  public boolean isInheritor(@Nonnull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  public PsiTypeParameterListOwner getOwner() {
    final PsiElement parent = getParent();
    if (parent == null) throw new PsiInvalidElementAccessException(this);
    final PsiElement parentParent = parent.getParent();
    if (!(parentParent instanceof PsiTypeParameterListOwner)) {
      // Might be an error element;
      return PsiTreeUtil.getParentOfType(this, PsiTypeParameterListOwner.class);
    }

    return (PsiTypeParameterListOwner)parentParent;
  }


  @Override
  public int getIndex() {
    final PsiTypeParameterStub stub = getStub();
    if (stub != null) {
      final PsiTypeParameterListStub parentStub = (PsiTypeParameterListStub)stub.getParentStub();
      return parentStub.getChildrenStubs().indexOf(stub);
    }

    int ret = 0;
    PsiElement element = getPrevSibling();
    while (element != null) {
      if (element instanceof PsiTypeParameter) {
        ret++;
      }
      element = element.getPrevSibling();
    }
    return ret;
  }

  @Override
  @Nonnull
  public PsiIdentifier getNameIdentifier() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiIdentifier.class);
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, PsiUtil.getLanguageLevel(place), false);
  }

  @Override
  public String getName() {
    final PsiTypeParameterStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }

    return getNameIdentifier().getText();
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  @Nonnull
  public PsiMethod[] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  @Nonnull
  public PsiReferenceList getExtendsList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.EXTENDS_BOUND_LIST);
  }

  @Override
  public PsiReferenceList getImplementsList() {
    return myLightEmptyImplementsList;
  }

  @Override
  @Nonnull
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @Override
  @Nonnull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiClass[] getInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiField[] getAllFields() {
    return PsiField.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiMethod[] getAllMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiClass[] getAllInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @Override
  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  @Override
  public PsiClass[] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  @Override
  @Nonnull
  public PsiClass[] getSupers() {
    return PsiClassImplUtil.getSupers(this);
  }

  @Override
  @Nonnull
  public PsiClassType[] getSuperTypes() {
    return PsiClassImplUtil.getSuperTypes(this);
  }

  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @Override
  @Nonnull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  public PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    return false;
  }

  @Override
  public PsiJavaToken getLBrace() {
    return null;
  }

  @Override
  public PsiJavaToken getRBrace() {
    return null;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeParameter(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @NonNls
  public String toString() {
    return "PsiTypeParameter:" + getName();
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isClassEquivalentTo(this, another);
  }

  @Override
  @Nonnull
  public SearchScope getUseScope() {
    return PsiClassImplUtil.getClassUseScope(this);
  }

  @Override
  @Nonnull
  public PsiAnnotation[] getAnnotations() {
    return getStubOrPsiChildren(JavaStubElementTypes.ANNOTATION, PsiAnnotation.ARRAY_FACTORY);
  }

  @Override
  public PsiAnnotation findAnnotation(@Nonnull @NonNls String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @Override
  @Nonnull
  public PsiAnnotation addAnnotation(@Nonnull @NonNls String qualifiedName) {
    throw new IncorrectOperationException();
  }

  @Override
  @Nonnull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }
}
