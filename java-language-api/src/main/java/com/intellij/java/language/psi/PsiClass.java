/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.JvmClass;
import com.intellij.java.language.jvm.JvmClassKind;
import com.intellij.java.language.jvm.types.JvmReferenceType;
import consulo.language.pom.PomRenameableTarget;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.PsiQualifiedNamedElement;
import consulo.language.psi.PsiTarget;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayFactory;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;
import java.util.List;

import static com.intellij.java.language.psi.PsiJvmConversionHelper.*;

/**
 * Represents a Java class or interface.
 *
 * @see PsiJavaFile#getClasses()
 */
public interface PsiClass extends PsiNameIdentifierOwner, PsiModifierListOwner, PsiDocCommentOwner, PsiTypeParameterListOwner, PsiQualifiedNamedElement, PsiTarget, PomRenameableTarget<PsiElement>,
    JvmClass {
  /**
   * The empty array of PSI classes which can be reused to avoid unnecessary allocations.
   */
  @Nonnull
  PsiClass[] EMPTY_ARRAY = new PsiClass[0];

  ArrayFactory<PsiClass> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiClass[count];

  /**
   * Returns the fully qualified name of the class.
   *
   * @return the qualified name of the class, or null for anonymous and local classes, and for type parameters
   */
  @Nullable
  String getQualifiedName();

  /**
   * Checks if the class is an interface.
   *
   * @return true if the class is an interface, false otherwise.
   */
  boolean isInterface();

  /**
   * Checks if the class is an annotation type.
   *
   * @return true if the class is an annotation type, false otherwise
   */
  boolean isAnnotationType();

  /**
   * Checks if the class is an enumeration.
   *
   * @return true if the class is an enumeration, false otherwise.
   */
  boolean isEnum();

  /**
   * Checks if the class is a record.
   *
   * @return true if the class is an record, false otherwise.
   */
  default boolean isRecord() {
    return false;
  }

  /**
   * Returns the list of classes that this class or interface extends.
   *
   * @return the extends list, or null for anonymous classes.
   */
  @Nullable
  PsiReferenceList getExtendsList();

  /**
   * Returns the list of interfaces that this class implements.
   *
   * @return the implements list, or null for anonymous classes
   */
  @Nullable
  PsiReferenceList getImplementsList();

  /**
   * Returns the list of class types for the classes that this class or interface extends.
   *
   * @return the list of extended class types, or an empty list for anonymous classes.
   */
  @Nonnull
  PsiClassType[] getExtendsListTypes();

  /**
   * Returns the list of class types for the interfaces that this class implements.
   *
   * @return the list of extended class types, or an empty list for anonymous classes,
   * enums and annotation types
   */
  @Nonnull
  PsiClassType[] getImplementsListTypes();

  /**
   * Returns the list of classes that this class or interface explicitly permits.
   *
   * @return the permits list, or null if there's none.
   */
  @Nullable
  default PsiReferenceList getPermitsList() {
    return null;
  }

  /**
   * Returns the array of class types that this class or interface explicitly permits.
   *
   * @return the array of explicitly permitted classes.
   */
  @Nonnull
  default PsiClassType[] getPermitsListTypes() {
    PsiReferenceList permitsList = getPermitsList();
    if (permitsList != null) {
      return permitsList.getReferencedTypes();
    }
    return PsiClassType.EMPTY_ARRAY;
  }

  /**
   * Returns the base class of this class.
   *
   * @return the base class. May return null when jdk is not configured, so no java.lang.Object is found,
   * or for java.lang.Object itself
   */
  @Nullable
  PsiClass getSuperClass();

  /**
   * Returns the list of interfaces implemented by the class, or extended by the interface.
   *
   * @return the list of interfaces.
   */
  @Nonnull
  PsiClass[] getInterfaces();

  /**
   * Returns the list of classes and interfaces extended or implemented by the class.
   *
   * @return the list of classes or interfaces. May return zero elements when jdk is
   * not configured, so no java.lang.Object is found
   */
  @Nonnull
  PsiClass[] getSupers();

  /**
   * Returns the list of class types for the classes and interfaces extended or
   * implemented by the class.
   *
   * @return the list of class types for the classes or interfaces.
   * For the class with no explicit extends list, the returned list always contains at least one element for the java.lang.Object type.
   * If psiClass is java.lang.Object, returned list is empty.
   */
  @Nonnull
  PsiClassType[] getSuperTypes();

  /**
   * Returns the list of fields in the class.
   *
   * @return the list of fields.
   */
  @Nonnull
  PsiField[] getFields();

  /**
   * Returns the list of methods in the class.
   *
   * @return the list of methods.
   */
  @Nonnull
  PsiMethod[] getMethods();

  /**
   * Returns the list of constructors for the class.
   *
   * @return the list of constructors,
   */
  @Nonnull
  PsiMethod[] getConstructors();

  /**
   * Returns the list of inner classes for the class.
   *
   * @return the list of inner classes.
   */
  @Nonnull
  PsiClass[] getInnerClasses();

  /**
   * Returns the list of class initializers for the class.
   *
   * @return the list of class initializers.
   */
  @Nonnull
  PsiClassInitializer[] getInitializers();

  /**
   * Returns the list of fields in the class and all its superclasses.
   *
   * @return the list of fields.
   */
  @Nonnull
  PsiField[] getAllFields();

  /**
   * Returns the list of methods in the class and all its superclasses.
   *
   * @return the list of methods.
   */
  @Nonnull
  PsiMethod[] getAllMethods();

  /**
   * Returns the list of inner classes for the class and all its superclasses.
   *
   * @return the list of inner classes.
   */
  @Nonnull
  PsiClass[] getAllInnerClasses();

  /**
   * Searches the class (and optionally its superclasses) for the field with the specified name.
   *
   * @param name       the name of the field to find.
   * @param checkBases if true, the field is also searched in the base classes of the class.
   * @return the field instance, or null if the field cannot be found.
   */
  @Nullable
  PsiField findFieldByName(@NonNls String name, boolean checkBases);

  /**
   * Searches the class (and optionally its superclasses) for the method with
   * the signature matching the signature of the specified method.
   *
   * @param patternMethod the method used as a pattern for the search.
   * @param checkBases    if true, the method is also searched in the base classes of the class.
   * @return the method instance, or null if the method cannot be found.
   */
  @Nullable
  PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases);

  /**
   * Searches the class (and optionally its superclasses) for the methods with the signature
   * matching the signature of the specified method. If the superclasses are not searched,
   * the method returns multiple results only in case of a syntax error (duplicate method).
   *
   * @param patternMethod the method used as a pattern for the search.
   * @param checkBases    if true, the method is also searched in the base classes of the class.
   * @return the found methods, or an empty array if no methods are found.
   */
  @Nonnull
  PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases);

  /**
   * Searches the class (and optionally its superclasses) for the methods with the specified name.
   *
   * @param name       the name of the methods to find.
   * @param checkBases if true, the methods are also searched in the base classes of the class.
   * @return the found methods, or an empty array if no methods are found.
   */
  @Nonnull
  PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases);

  /**
   * Searches the class (and optionally its superclasses) for the methods with the specified name
   * and returns the methods along with their substitutors.
   *
   * @param name       the name of the methods to find.
   * @param checkBases if true, the methods are also searched in the base classes of the class.
   * @return the found methods and their substitutors, or an empty list if no methods are found.
   */
  @Nonnull
  List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls String name, boolean checkBases);

  /**
   * Returns the list of methods in the class and all its superclasses, along with their
   * substitutors.
   *
   * @return the list of methods and their substitutors
   */
  @Nonnull
  List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors();

  /**
   * Searches the class (and optionally its superclasses) for the inner class with the specified name.
   *
   * @param name       the name of the inner class to find.
   * @param checkBases if true, the inner class is also searched in the base classes of the class.
   * @return the inner class instance, or null if the inner class cannot be found.
   */
  @Nullable
  PsiClass findInnerClassByName(@NonNls String name, boolean checkBases);

  /**
   * Returns the token representing the opening curly brace of the class.
   *
   * @return the token instance, or null if the token is missing in the source code file.
   */
  @Nullable
  PsiElement getLBrace();

  /**
   * Returns the token representing the closing curly brace of the class.
   *
   * @return the token instance, or null if the token is missing in the source code file.
   */
  @Nullable
  PsiElement getRBrace();

  /**
   * Returns the name identifier of the class.
   *
   * @return the name identifier, or null if the class is anonymous or synthetic jsp class
   */
  @Override
  @Nullable
  PsiIdentifier getNameIdentifier();

  /**
   * Returns the PSI member in which the class has been declared (for example,
   * the method containing the anonymous inner class, or the file containing a regular
   * class, or the class owning a type parameter).
   *
   * @return the member in which the class has been declared.
   */
  PsiElement getScope();

  /**
   * Checks if this class is an inheritor of the specified base class.
   * Only java inheritance rules are considered.
   * Note that {@link com.intellij.psi.search.searches.ClassInheritorsSearch}
   * may return classes that are inheritors in broader, e.g. in ejb sense, but not in java sense.
   *
   * @param baseClass the base class to check the inheritance.
   * @param checkDeep if false, only direct inheritance is checked; if true, the base class is
   *                  searched in the entire inheritance chain
   * @return true if the class is an inheritor, false otherwise
   */
  boolean isInheritor(@Nonnull PsiClass baseClass, boolean checkDeep);

  /**
   * Checks if this class is a deep inheritor of the specified base class possibly bypassing a class
   * when checking inheritance chain.
   * Only java inheritance rules are considered.
   * Note that {@link com.intellij.psi.search.searches.ClassInheritorsSearch}
   * may return classes that are inheritors in broader, e.g. in ejb sense, but not in java sense.
   *
   * @param baseClass     the base class to check the inheritance.
   *                      searched in the entire inheritance chain
   * @param classToByPass class to bypass the inheratance check for
   * @return true if the class is an inheritor, false otherwise
   */
  boolean isInheritorDeep(PsiClass baseClass, @Nullable PsiClass classToByPass);

  /**
   * For an inner class, returns its containing class.
   *
   * @return the containing class, or null if the class is not an inner class.
   */
  @Override
  @Nullable
  PsiClass getContainingClass();

  /**
   * Returns the hierarchical signatures for all methods in the specified class and
   * its superclasses and superinterfaces.
   *
   * @return the collection of signatures.
   * @since 5.1
   */
  @Nonnull
  Collection<HierarchicalMethodSignature> getVisibleSignatures();

  @Override
  PsiElement setName(@NonNls @Nonnull String name) throws IncorrectOperationException;

  @Nonnull
  @Override
  default JvmClassKind getClassKind() {
    return getJvmClassKind(this);
  }

  @Nullable
  @Override
  default JvmReferenceType getSuperClassType() {
    return getClassSuperType(this);
  }

  @Nonnull
  @Override
  default JvmReferenceType[] getInterfaceTypes() {
    return getClassInterfaces(this);
  }

  @Nonnull
  default PsiRecordComponent[] getRecordComponents() {
    return PsiRecordComponent.EMPTY_ARRAY;
  }

  @Nullable
  default PsiRecordHeader getRecordHeader() {
    return null;
  }
}