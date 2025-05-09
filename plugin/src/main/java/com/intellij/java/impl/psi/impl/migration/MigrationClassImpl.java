/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.psi.impl.migration;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.impl.psi.impl.InheritanceImplUtil;
import com.intellij.java.language.impl.psi.impl.PsiClassImplUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.content.scope.SearchScope;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.meta.PsiMetaData;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author dsl
 */
public class MigrationClassImpl extends LightElement implements PsiClass{
  private final String myQualifiedName;
  private final String myName;
  private final PsiMigrationImpl myMigration;

  MigrationClassImpl(PsiMigrationImpl migration, String qualifiedName) {
    super(migration.getManager(), JavaLanguage.INSTANCE);
    myMigration = migration;
    myQualifiedName = qualifiedName;
    myName = PsiNameHelper.getShortClassName(myQualifiedName);
  }

  public String toString() {
    return "MigrationClass:" + myQualifiedName;
  }

  @Override
  public String getText() {
    return "class " + myName + " {}";
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

  @Override
  public PsiElement copy() {
    return new MigrationClassImpl(myMigration, myQualifiedName);
  }

  @Override
  public boolean isValid() {
    return myMigration.isValid();
  }

  @Override
  public String getQualifiedName() {
    return myQualifiedName;
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
  public PsiReferenceList getExtendsList() {
    return null;
  }


  @Override
  public PsiReferenceList getImplementsList() {
    return null;
  }

  @Override
  @Nonnull
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  public PsiClass getSuperClass() {
    return JavaPsiFacade.getInstance(myManager.getProject())
      .findClass(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(myManager.getProject()));
  }

  @Override
  public PsiClass[] getInterfaces() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiClass[] getSupers() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiClassType[] getSuperTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @Override
  @Nonnull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return Collections.emptySet();
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
  @Nonnull
  public PsiMethod[] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiClass[] getInnerClasses() {
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
  public PsiField findFieldByName(String name, boolean checkBases) {
    return null;
  }

  @Override
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return null;
  }

  @Override
  @Nonnull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return new ArrayList<Pair<PsiMethod,PsiSubstitutor>>();
  }

  @Override
  @Nonnull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return new ArrayList<Pair<PsiMethod,PsiSubstitutor>>();
  }

  @Override
  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    return null;
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  public boolean hasTypeParameters() {
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
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  // very special method!
  @Override
  public PsiElement getScope() {
    return null;
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
  public String getName() {
    return myName;
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    return PsiModifier.PUBLIC.equals(name);
  }

  @Override
  public PsiDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  public PsiMetaData getMetaData() {
    return null;
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
}
