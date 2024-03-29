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
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import consulo.application.util.function.Computable;
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
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.util.List;

/**
 * @author peter
 */
public class LightMethodBuilder extends LightElement implements PsiMethod, OriginInfoAwareElement {
  private final String myName;
  private Computable<PsiType> myReturnType;
  private final PsiModifierList myModifierList;
  private PsiParameterList myParameterList;
  private PsiTypeParameterList myTypeParameterList;
  private PsiReferenceList myThrowsList;
  private Icon myBaseIcon;
  private PsiClass myContainingClass;
  private boolean myConstructor;
  private String myMethodKind = "LightMethodBuilder";
  private String myOriginInfo = null;

  public LightMethodBuilder(PsiClass constructedClass, Language language) {
    this(constructedClass.getManager(), language, constructedClass.getName());
    setContainingClass(constructedClass);
  }

  public LightMethodBuilder(PsiManager manager, String name) {
    this(manager, JavaLanguage.INSTANCE, name);
  }

  public LightMethodBuilder(PsiManager manager, Language language, String name) {
    this(manager, language, name, new LightParameterListBuilder(manager, language), new LightModifierList(manager, language));
  }

  public LightMethodBuilder(PsiManager manager,
                            Language language,
                            String name,
                            PsiParameterList parameterList,
                            PsiModifierList modifierList) {
    this(manager, language, name, parameterList, modifierList,
        new LightReferenceListBuilder(manager, language, PsiReferenceList.Role.THROWS_LIST),
        new LightTypeParameterListBuilder(manager, language));
  }

  public LightMethodBuilder(PsiManager manager,
                            Language language,
                            String name,
                            PsiParameterList parameterList,
                            PsiModifierList modifierList,
                            PsiReferenceList throwsList,
                            PsiTypeParameterList typeParameterList) {
    super(manager, language);
    myName = name;
    myParameterList = parameterList;
    myModifierList = modifierList;
    myThrowsList = throwsList;
    myTypeParameterList = typeParameterList;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProvider.getItemPresentation(this);
  }

  @Override
  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @Override
  @Nonnull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return myTypeParameterList;
  }

  @Override
  public PsiDocComment getDocComment() {
    //todo
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return PsiImplUtil.isDeprecatedByDocTag(this) || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    final String className = myContainingClass == null ? "null" : myContainingClass.getQualifiedName();
    throw new UnsupportedOperationException("Please don't rename light methods: writable=" + isWritable() +
        "; class=" + getClass() +
        "; name=" + getName() +
        "; inClass=" + className);
  }

  @Override
  @Nonnull
  public String getName() {
    return myName;
  }

  @Override
  @Nonnull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  @Nonnull
  public PsiModifierList getModifierList() {
    return myModifierList;
  }

  public LightMethodBuilder addModifiers(String... modifiers) {
    for (String modifier : modifiers) {
      addModifier(modifier);
    }
    return this;
  }

  public LightMethodBuilder addModifier(String modifier) {
    ((LightModifierList) myModifierList).addModifier(modifier);
    return this;
  }

  public LightMethodBuilder setModifiers(String... modifiers) {
    ((LightModifierList) myModifierList).clearModifiers();
    addModifiers(modifiers);
    return this;
  }

  @Override
  public PsiType getReturnType() {
    return myReturnType == null ? null : myReturnType.compute();
  }

  public LightMethodBuilder setMethodReturnType(Computable<PsiType> returnType) {
    myReturnType = returnType;
    return this;
  }

  public LightMethodBuilder setMethodReturnType(PsiType returnType) {
    return setMethodReturnType(new Computable.PredefinedValueComputable<PsiType>(returnType));
  }

  public LightMethodBuilder setMethodReturnType(@Nonnull final String returnType) {
    return setMethodReturnType(new Computable.NotNullCachedComputable<PsiType>() {
      @Nonnull
      @Override
      protected PsiType internalCompute() {
        return JavaPsiFacade.getInstance(getManager().getProject()).getElementFactory().createTypeByFQClassName(returnType, getResolveScope());
      }
    });
  }

  @Override
  public PsiTypeElement getReturnTypeElement() {
    return null;
  }

  @Override
  @Nonnull
  public PsiParameterList getParameterList() {
    return myParameterList;
  }

  public LightMethodBuilder addParameter(@Nonnull PsiParameter parameter) {
    ((LightParameterListBuilder) myParameterList).addParameter(parameter);
    return this;
  }

  public LightMethodBuilder addParameter(@Nonnull String name, @Nonnull String type) {
    return addParameter(name, JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(type, this));
  }

  public LightMethodBuilder addParameter(@Nonnull String name, @Nonnull PsiType type) {
    return addParameter(new LightParameter(name, type, this, JavaLanguage.INSTANCE));
  }

  public LightMethodBuilder addParameter(@Nonnull String name, @Nonnull PsiType type, boolean isVarArgs) {
    if (isVarArgs && !(type instanceof PsiEllipsisType)) {
      type = new PsiEllipsisType(type);
    }
    return addParameter(new LightParameter(name, type, this, JavaLanguage.INSTANCE, isVarArgs));
  }

  public LightMethodBuilder addException(PsiClassType type) {
    ((LightReferenceListBuilder) myThrowsList).addReference(type);
    return this;
  }

  public LightMethodBuilder addException(String fqName) {
    ((LightReferenceListBuilder) myThrowsList).addReference(fqName);
    return this;
  }


  @Override
  @Nonnull
  public PsiReferenceList getThrowsList() {
    return myThrowsList;
  }

  @Override
  public PsiCodeBlock getBody() {
    return null;
  }

  public LightMethodBuilder setConstructor(boolean constructor) {
    myConstructor = constructor;
    return this;
  }

  @Override
  public boolean isConstructor() {
    return myConstructor;
  }

  @Override
  public boolean isVarArgs() {
    return PsiImplUtil.isVarArgs(this);
  }

  @Override
  @Nonnull
  public MethodSignature getSignature(@Nonnull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  @Nonnull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @Override
  @Nonnull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @Override
  @Nonnull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @Override
  @Nonnull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  @Override
  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @Override
  @Nonnull
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitMethod(this);
    }
  }

  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  public LightMethodBuilder setContainingClass(PsiClass containingClass) {
    myContainingClass = containingClass;
    return this;
  }

  public LightMethodBuilder setMethodKind(String debugKindName) {
    myMethodKind = debugKindName;
    return this;
  }

  public String toString() {
    return myMethodKind + ":" + getName();
  }

  public LightMethodBuilder setBaseIcon(Icon baseIcon) {
    myBaseIcon = baseIcon;
    return this;
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @Override
  @Nonnull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    final PsiClass containingClass = getContainingClass();
    return containingClass == null ? null : containingClass.getContainingFile();
  }

  @Override
  public PsiElement getContext() {
    final PsiElement navElement = getNavigationElement();
    if (navElement != this) {
      return navElement;
    }

    final PsiClass cls = getContainingClass();
    if (cls != null) {
      return cls;
    }

    return getContainingFile();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LightMethodBuilder that = (LightMethodBuilder) o;

    if (myConstructor != that.myConstructor) return false;
    if (myBaseIcon != null ? !myBaseIcon.equals(that.myBaseIcon) : that.myBaseIcon != null) return false;
    if (myContainingClass != null ? !myContainingClass.equals(that.myContainingClass) : that.myContainingClass != null)
      return false;
    if (!myMethodKind.equals(that.myMethodKind)) return false;
    if (!myModifierList.equals(that.myModifierList)) return false;
    if (!myName.equals(that.myName)) return false;
    if (!myParameterList.equals(that.myParameterList)) return false;
    if (myReturnType != null ? !myReturnType.equals(that.myReturnType) : that.myReturnType != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + (myReturnType != null ? myReturnType.hashCode() : 0);
    result = 31 * result + myModifierList.hashCode();
    result = 31 * result + myParameterList.hashCode();
    result = 31 * result + (myBaseIcon != null ? myBaseIcon.hashCode() : 0);
    result = 31 * result + (myContainingClass != null ? myContainingClass.hashCode() : 0);
    result = 31 * result + (myConstructor ? 1 : 0);
    result = 31 * result + myMethodKind.hashCode();
    return result;
  }

  public LightMethodBuilder addTypeParameter(PsiTypeParameter parameter) {
    ((LightTypeParameterListBuilder) myTypeParameterList).addParameter(new LightTypeParameter(parameter));
    return this;
  }

  @Nullable
  @Override
  public String getOriginInfo() {
    return myOriginInfo;
  }

  public void setOriginInfo(@Nullable String originInfo) {
    myOriginInfo = originInfo;
  }

}
