/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.compiled;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.InheritanceImplUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterStub;
import com.intellij.psi.impl.light.LightEmptyImplementsList;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 */
public class ClsTypeParameterImpl extends ClsRepositoryPsiElement<PsiTypeParameterStub> implements PsiTypeParameter {
  private final LightEmptyImplementsList myLightEmptyImplementsList;

  public ClsTypeParameterImpl(@Nonnull PsiTypeParameterStub stub) {
    super(stub);
    myLightEmptyImplementsList = new LightEmptyImplementsList(getManager());
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
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor, @Nonnull ResolveState state, PsiElement lastParent, @Nonnull PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, PsiUtil.getLanguageLevel(place), false);
  }

  @Override
  public String getName() {
    return getStub().getName();
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot change compiled classes");
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
    return getStub().findChildStubByType(JavaStubElementTypes.EXTENDS_BOUND_LIST).getPsi();
  }

  @Override
  public PsiReferenceList getImplementsList() {
    return myLightEmptyImplementsList;
  }

  @Override
  @Nonnull
  public PsiClassType[] getExtendsListTypes() {
    return getExtendsList().getReferencedTypes();
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
    return "PsiTypeParameter";
  }

  @Override
  public void appendMirrorText(int indentLevel, @Nonnull StringBuilder buffer) {
    buffer.append(getName());

    PsiJavaCodeReferenceElement[] bounds = getExtendsList().getReferenceElements();
    if (bounds.length > 0) {
      buffer.append(" extends ");
      for (int i = 0; i < bounds.length; i++) {
        if (i > 0) buffer.append(" & ");
        buffer.append(bounds[i].getCanonicalText());
      }
    }
  }

  @Override
  public void setMirror(@Nonnull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);
    setMirror(getExtendsList(), SourceTreeToPsiMap.<PsiTypeParameter>treeToPsiNotNull(element).getExtendsList());
  }

  @Override
  @Nonnull
  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public PsiTypeParameterListOwner getOwner() {
    return (PsiTypeParameterListOwner)getParent().getParent();
  }

  @Override
  public int getIndex() {
    final PsiTypeParameterStub stub = getStub();
    return stub.getParentStub().getChildrenStubs().indexOf(stub);
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

  //todo parse annotataions
  @Override
  @Nonnull
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public PsiAnnotation findAnnotation(@Nonnull @NonNls String qualifiedName) {
    return null;
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
