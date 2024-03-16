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
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.impl.psi.impl.InheritanceImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.Language;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import consulo.language.psi.SyntheticElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.List;

public abstract class LightPsiClassBase extends LightElement implements PsiClass, SyntheticElement {

  private final @Nonnull String myName;

  public LightPsiClassBase(@Nonnull PsiElement context, @Nonnull String name) {
    this(context.getManager(), context.getLanguage(), name);
  }

  public LightPsiClassBase(@Nonnull PsiManager manager, @Nonnull Language language, @Nonnull String name) {
    super(manager, language);
    myName = name;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClass(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Nonnull
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getQualifiedName() {
    PsiElement parent = getParent();
    if (parent instanceof PsiJavaFile) {
      return StringUtil.getQualifiedName(((PsiJavaFile)parent).getPackageName(), getName());
    }
    if (parent instanceof PsiClass) {
      String parentQName = ((PsiClass)parent).getQualifiedName();
      if (parentQName == null) return null;
      return StringUtil.getQualifiedName(parentQName, getName());
    }
    return null;
  }

  @Override
  public String toString() {
    return "Light PSI class: " + getName();
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
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @Override
  @Nonnull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassImplUtil.getImplementsListTypes(this);
  }

  @Nullable
  @Override
  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  @Override
  @Nonnull
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
  @Nonnull
  public PsiMethod[] getConstructors() {
    return PsiImplUtil.getConstructors(this);
  }

  @Override
  @Nonnull
  public PsiField[] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  @Override
  @Nonnull
  public PsiMethod[] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  @Override
  @Nonnull
  public PsiClass[] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  @Nullable
  @Override
  public PsiField findFieldByName(@NonNls String name, boolean checkBases) {
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  @Nullable
  @Override
  public PsiMethod findMethodBySignature(@Nonnull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  @Nonnull
  public PsiMethod[] findMethodsBySignature(@Nonnull PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  @Nonnull
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @Nonnull
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls @Nonnull String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Nonnull
  @Override
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiClassImplUtil.MemberType.METHOD);
  }

  @Nullable
  @Override
  public PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    return PsiClassImplUtil.findInnerByName(this, name, checkBases);
  }

  @Nullable
  @Override
  public PsiElement getLBrace() {
    return null;
  }

  @Nullable
  @Override
  public PsiElement getRBrace() {
    return null;
  }

  @Nullable
  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public boolean isInheritor(@Nonnull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  public boolean isInheritorDeep(@Nonnull PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Nonnull
  @Override
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @Override
  public PsiElement setName(@NonNls @Nonnull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot rename light class");
  }

  @Nullable
  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  @Nonnull
  public PsiTypeParameter [] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @Nonnull
  @Override
  public abstract PsiModifierList getModifierList();

  @Override
  public boolean hasModifierProperty(@PsiModifier.ModifierConstant @NonNls @Nonnull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(
      this, processor, state, null, lastParent, place, PsiUtil.getLanguageLevel(place), false
    );
  }
}
