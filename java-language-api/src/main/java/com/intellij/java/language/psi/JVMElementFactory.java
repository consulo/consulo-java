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
package com.intellij.java.language.psi;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;

import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * @author Medvedev Max
 */
public interface JVMElementFactory {
  /**
   * Creates an empty class with the specified name.
   *
   * @param name the name of the class to create.
   * @return the created class instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid Java identifier.
   */
  PsiClass createClass(String name) throws IncorrectOperationException;

  /**
   * Creates an empty interface with the specified name.
   *
   * @param name the name of the interface to create.
   * @return the created interface instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid Java identifier.
   */
  PsiClass createInterface(String name) throws IncorrectOperationException;

  /**
   * Creates an empty enum with the specified name.
   *
   * @param name the name of the enum to create.
   * @return the created enum instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid Java identifier.
   */
  PsiClass createEnum(String name) throws IncorrectOperationException;

  /**
   * Creates a field with the specified name and type.
   *
   * @param name the name of the field to create.
   * @param type the type of the field to create.
   * @return the created field instance.
   * @throws IncorrectOperationException <code>name</code> is not a valid Java identifier
   *                                     or <code>type</code> represents an invalid type.
   */
  PsiField createField(String name, PsiType type) throws IncorrectOperationException;

  /**
   * Creates an empty method with the specified name and return type.
   *
   * @param name       the name of the method to create.
   * @param returnType the return type of the method to create.
   * @return the created method instance.
   * @throws IncorrectOperationException <code>name</code> is not a valid Java identifier
   *                                     or <code>type</code> represents an invalid type.
   */
  PsiMethod createMethod(String name, PsiType returnType) throws IncorrectOperationException;

  PsiMethod createMethod(String name,
                         PsiType returnType,
                         PsiElement context) throws IncorrectOperationException;

  /**
   * Creates an empty constructor.
   *
   * @return the created constructor instance.
   */
  PsiMethod createConstructor();

  /**
   * Creates an empty class initializer block.
   *
   * @return the created initializer block instance.
   * @throws IncorrectOperationException in case of an internal error.
   */
  PsiClassInitializer createClassInitializer() throws IncorrectOperationException;

  /**
   * Creates a parameter with the specified name and type.
   *
   * @param name the name of the parameter to create.
   * @param type the type of the parameter to create.
   * @return the created parameter instance.
   * @throws IncorrectOperationException <code>name</code> is not a valid Java identifier
   *                                     or <code>type</code> represents an invalid type.
   */
  PsiParameter createParameter(String name, PsiType type) throws IncorrectOperationException;

  PsiParameter createParameter(String name,
                               PsiType type,
                               PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a parameter list from the specified parameter names and types.
   *
   * @param names the array of names for the parameters to create.
   * @param types the array of types for the parameters to create.
   * @return the created parameter list.
   * @throws IncorrectOperationException if some of the parameter names or types are invalid.
   */
  PsiParameterList createParameterList(String[] names,
                                       PsiType[] types) throws IncorrectOperationException;

  PsiMethod createMethodFromText(String text, @Nullable PsiElement context);

  PsiAnnotation createAnnotationFromText(String annotationText,
                                         @Nullable PsiElement context) throws IncorrectOperationException;

  PsiElement createExpressionFromText(String text,
                                                           @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates new reference element by type
   *
   * @param type the type for which reference element should be created
   * @return the reference element
   */
  PsiElement createReferenceElementByType(PsiClassType type);

  /**
   * Creates empty type parameter list
   *
   * @return the empty type parameter list
   */
  PsiTypeParameterList createTypeParameterList();

  /**
   * Creates new type parameter with the specified name and super types
   *
   * @param name       the name which the type parameter should have
   * @param superTypes the super types of the type parameter
   * @return the new type parameter
   */
  PsiTypeParameter createTypeParameter(String name, PsiClassType[] superTypes);

  /**
   * Creates a class type for the specified class.
   *
   * @param aClass the class for which the class type is created.
   * @return the class type instance.
   */
  PsiClassType createType(PsiClass aClass);

  /**
   * Creates an empty annotation type with the specified name.
   *
   * @param name the name of the annotation type to create.
   * @return the created annotation type instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid Java identifier.
   */
  PsiClass createAnnotationType(String name) throws IncorrectOperationException;

  /**
   * Creates an empty constructor with a given name.
   *
   * @param name the name of the constructor to create.
   * @return the created constructor instance.
   */
  PsiMethod createConstructor(String name);

  PsiMethod createConstructor(String name, PsiElement context);

  /**
   * Creates a class type for the specified class, using the specified substitutor
   * to replace generic type parameters on the class.
   *
   * @param resolve     the class for which the class type is created.
   * @param substitutor the substitutor to use.
   * @return the class type instance.
   */
  PsiClassType createType(PsiClass resolve, PsiSubstitutor substitutor);

  /*
     additional languageLevel parameter to memorize language level for allowing/prohibiting boxing/unboxing
    */
  PsiClassType createType(PsiClass resolve,
                          PsiSubstitutor substitutor,
                          LanguageLevel languageLevel);

  PsiClassType createType(PsiClass resolve,
                          PsiSubstitutor substitutor,
                          LanguageLevel languageLevel,
                          PsiAnnotation[] annotations);

  PsiClassType createType(PsiClass aClass, PsiType parameters);

  PsiClassType createType(PsiClass aClass, PsiType... parameters);

  /**
   * Creates a substitutor for the specified class which replaces all type parameters
   * with their corresponding raw types.
   *
   * @param owner the class or method for which the substitutor is created.
   * @return the substitutor instance.
   */
  PsiSubstitutor createRawSubstitutor(PsiTypeParameterListOwner owner);

  /**
   * Creates a substitutor which uses the specified mapping between type parameters and types.
   *
   * @param map the type parameter to type map used by the substitutor.
   * @return the substitutor instance.
   */
  PsiSubstitutor createSubstitutor(Map<PsiTypeParameter, PsiType> map);

  /**
   * Returns the primitive type instance for the specified type name.
   *
   * @param text the name of a Java primitive type (for example, <code>int</code>)
   * @return the primitive type instance, or null if <code>name</code> is not a valid
   * primitive type name.
   */
  @Nullable
  PsiPrimitiveType createPrimitiveType(String text);

  /**
   * The same as {@link #createTypeByFQClassName(String, GlobalSearchScope)}
   * with {@link GlobalSearchScope#allScope(Project)}.
   *
   * @param qName the full-qualified name of the class to create the reference to.
   * @return the class type instance.
   */
  PsiClassType createTypeByFQClassName(String qName);

  /**
   * Creates a class type referencing a class with the specified class name in the specified
   * search scope.
   *
   * @param qName        the full-qualified name of the class to create the reference to.
   * @param resolveScope the scope in which the class is searched.
   * @return the class type instance.
   */
  PsiClassType createTypeByFQClassName(String qName, GlobalSearchScope resolveScope);

  /**
   * Creates doc comment from text
   */
  PsiDocComment createDocCommentFromText(String text);

  /**
   * Checks whether name is a valid class name in the current language
   *
   * @param name name to checks
   * @return true if name is a valid name
   */
  boolean isValidClassName(String name);

  /**
   * Checks whether name is a valid method name in the current language
   *
   * @param name name to checks
   * @return true if name is a valid name
   */
  boolean isValidMethodName(String name);

  /**
   * Checks whether name is a valid parameter name in the current language
   *
   * @param name name to checks
   * @return true if name is a valid name
   */
  boolean isValidParameterName(String name);

  /**
   * Checks whether name is a valid field name in the current language
   *
   * @param name name to checks
   * @return true if name is a valid name
   */
  boolean isValidFieldName(String name);

  /**
   * Checks whether name is a valid local variable name in the current language
   *
   * @param name name to checks
   * @return true if name is a valid name
   */
  boolean isValidLocalVariableName(String name);
}

