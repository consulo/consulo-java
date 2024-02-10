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
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.ide.ServiceManager;
import consulo.language.parser.PsiBuilder;
import consulo.language.parser.PsiParser;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * Service for creating instances of Java, JavaDoc, JSP and XML PSI elements which don't have
 * an underlying source code file.
 *
 * @see JavaPsiFacade#getElementFactory()
 */
@ServiceAPI(ComponentScope.PROJECT)
public interface PsiElementFactory extends PsiJavaParserFacade, JVMElementFactory {
  /**
   * @deprecated please use {@link #getInstance(Project)}
   */
  @Deprecated
  class SERVICE {
    private SERVICE() {
    }

    public static PsiElementFactory getInstance(Project project) {
      return PsiElementFactory.getInstance(project);
    }
  }

  static PsiElementFactory getInstance(Project project) {
    return ServiceManager.getService(project, PsiElementFactory.class);
  }

  /**
   * Creates an empty class with the specified name.
   *
   * @param name the name of the class to create.
   * @return the created class instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid Java identifier.
   */
  @Override
  @Nonnull
  PsiClass createClass(@NonNls @Nonnull String name) throws IncorrectOperationException;

  /**
   * Creates an empty interface with the specified name.
   *
   * @param name the name of the interface to create.
   * @return the created interface instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid Java identifier.
   */
  @Override
  @Nonnull
  PsiClass createInterface(@NonNls @Nonnull String name) throws IncorrectOperationException;

  /**
   * Creates an empty enum with the specified name.
   *
   * @param name the name of the enum to create.
   * @return the created enum instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid Java identifier.
   */
  @Override
  @Nonnull
  PsiClass createEnum(@Nonnull @NonNls String name) throws IncorrectOperationException;

  /**
   * Creates an empty annotation type with the specified name.
   *
   * @param name the name of the annotation type to create.
   * @return the created annotation type instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid Java identifier.
   */
  @Nonnull
  PsiClass createAnnotationType(@Nonnull @NonNls String name) throws IncorrectOperationException;

  /**
   * Creates a field with the specified name and type.
   *
   * @param name the name of the field to create.
   * @param type the type of the field to create.
   * @return the created field instance.
   * @throws IncorrectOperationException <code>name</code> is not a valid Java identifier
   *                                     or <code>type</code> represents an invalid type.
   */
  @Override
  @Nonnull
  PsiField createField(@Nonnull @NonNls String name, @Nonnull PsiType type) throws IncorrectOperationException;

  /**
   * Creates an empty method with the specified name and return type.
   *
   * @param name       the name of the method to create.
   * @param returnType the return type of the method to create.
   * @return the created method instance.
   * @throws IncorrectOperationException <code>name</code> is not a valid Java identifier
   *                                     or <code>type</code> represents an invalid type.
   */
  @Override
  @Nonnull
  PsiMethod createMethod(@Nonnull @NonNls String name, PsiType returnType) throws IncorrectOperationException;

  /**
   * Creates an empty constructor.
   *
   * @return the created constructor instance.
   */
  @Override
  @Nonnull
  PsiMethod createConstructor();

  /**
   * Creates an empty constructor with a given name.
   *
   * @param name the name of the constructor to create.
   * @return the created constructor instance.
   */
  @Nonnull
  PsiMethod createConstructor(@Nonnull @NonNls String name);

  /**
   * Creates an empty class initializer block.
   *
   * @return the created initializer block instance.
   * @throws IncorrectOperationException in case of an internal error.
   */
  @Override
  @Nonnull
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
  @Override
  @Nonnull
  PsiParameter createParameter(@Nonnull @NonNls String name, @Nonnull PsiType type) throws IncorrectOperationException;

  /**
   * Creates an empty Java code block.
   *
   * @return the created code block instance.
   */
  @Nonnull
  PsiCodeBlock createCodeBlock();

  /**
   * Creates a class type for the specified class, using the specified substitutor
   * to replace generic type parameters on the class.
   *
   * @param resolve     the class for which the class type is created.
   * @param substitutor the substitutor to use.
   * @return the class type instance.
   */
  @Nonnull
  PsiClassType createType(@Nonnull PsiClass resolve, @Nonnull PsiSubstitutor substitutor);

  /**
   * Creates a class type for the specified class, using the specified substitutor
   * to replace generic type parameters on the class.
   *
   * @param resolve       the class for which the class type is created.
   * @param substitutor   the substitutor to use.
   * @param languageLevel to memorize language level for allowing/prohibiting boxing/unboxing.
   * @return the class type instance.
   */
  @Nonnull
  PsiClassType createType(@Nonnull PsiClass resolve, @Nonnull PsiSubstitutor substitutor, @Nullable LanguageLevel languageLevel);

  @Nonnull
  PsiClassType createType(@Nonnull PsiClass resolve, @Nonnull PsiSubstitutor substitutor, @Nullable LanguageLevel languageLevel, @Nonnull PsiAnnotation[] annotations);

  /**
   * Creates a class type for the specified reference pointing to a class.
   *
   * @param classReference the class reference for which the class type is created.
   * @return the class type instance.
   */
  @Nonnull
  PsiClassType createType(@Nonnull PsiJavaCodeReferenceElement classReference);

  @Nonnull
  PsiClassType createType(@Nonnull PsiClass aClass, PsiType parameters);

  @Nonnull
  PsiClassType createType(@Nonnull PsiClass aClass, PsiType... parameters);

  /**
   * Creates a substitutor for the specified class which replaces all type parameters
   * with their corresponding raw types.
   *
   * @param owner the class or method for which the substitutor is created.
   * @return the substitutor instance.
   */
  @Nonnull
  PsiSubstitutor createRawSubstitutor(@Nonnull PsiTypeParameterListOwner owner);

  /**
   * Creates a substitutor which uses the specified mapping between type parameters and types.
   *
   * @param map the type parameter to type map used by the substitutor.
   * @return the substitutor instance.
   */
  @Nonnull
  PsiSubstitutor createSubstitutor(@Nonnull Map<PsiTypeParameter, PsiType> map);

  /**
   * Returns the primitive type instance for the specified type name.
   *
   * @param text the name of a Java primitive type (for example, <code>int</code>)
   * @return the primitive type instance, or null if <code>name</code> is not a valid
   * primitive type name.
   */
  @Nullable
  PsiPrimitiveType createPrimitiveType(@Nonnull String text);

  /**
   * The same as {@link #createTypeByFQClassName(String, GlobalSearchScope)}
   * with {@link GlobalSearchScope#allScope(Project)}.
   *
   * @param qName the full-qualified name of the class to create the reference to.
   * @return the class type instance.
   */
  @Nonnull
  PsiClassType createTypeByFQClassName(@Nonnull @NonNls String qName);

  /**
   * Creates a class type referencing a class with the specified class name in the specified
   * search scope.
   *
   * @param qName        the full-qualified name of the class to create the reference to.
   * @param resolveScope the scope in which the class is searched.
   * @return the class type instance.
   */
  @Nonnull
  PsiClassType createTypeByFQClassName(@Nonnull @NonNls String qName, @Nonnull GlobalSearchScope resolveScope);

  /**
   * Creates a type element referencing the specified type.
   *
   * @param psiType the type to reference.
   * @return the type element instance.
   */
  @Nonnull
  PsiTypeElement createTypeElement(@Nonnull PsiType psiType);

  /**
   * Creates a reference element resolving to the specified class type.
   *
   * @param type the class type to create the reference to.
   * @return the reference element instance.
   */
  @Nonnull
  PsiJavaCodeReferenceElement createReferenceElementByType(@Nonnull PsiClassType type);

  /**
   * Creates a reference element resolving to the specified class.
   *
   * @param aClass the class to create the reference to.
   * @return the reference element instance.
   */
  @Nonnull
  PsiJavaCodeReferenceElement createClassReferenceElement(@Nonnull PsiClass aClass);

  /**
   * Creates a reference element resolving to the class with the specified name
   * in the specified search scope. The text of the created reference is the short name of the class.
   *
   * @param qName        the full-qualified name of the class to create the reference to.
   * @param resolveScope the scope in which the class is searched.
   * @return the reference element instance.
   */
  @Nonnull
  PsiJavaCodeReferenceElement createReferenceElementByFQClassName(@Nonnull String qName, @Nonnull GlobalSearchScope resolveScope);

  /**
   * Creates a reference element resolving to the class with the specified name
   * in the specified search scope. The text of the created reference is the fully qualified name of the class.
   *
   * @param qName        the full-qualified name of the class to create the reference to.
   * @param resolveScope the scope in which the class is searched.
   * @return the reference element instance.
   */
  @Nonnull
  PsiJavaCodeReferenceElement createFQClassNameReferenceElement(@Nonnull String qName, @Nonnull GlobalSearchScope resolveScope);

  /**
   * Creates a reference element resolving to the specified package.
   *
   * @param aPackage the package to create the reference to.
   * @return the reference element instance.
   * @throws IncorrectOperationException if <code>aPackage</code> is the default (root) package.
   */
  @Nonnull
  PsiJavaCodeReferenceElement createPackageReferenceElement(@Nonnull PsiJavaPackage aPackage) throws IncorrectOperationException;

  /**
   * Creates a reference element resolving to the package with the specified name.
   *
   * @param packageName the name of the package to create the reference to.
   * @return the reference element instance.
   * @throws IncorrectOperationException if <code>packageName</code> is an empty string.
   */
  @Nonnull
  PsiJavaCodeReferenceElement createPackageReferenceElement(@Nonnull String packageName) throws IncorrectOperationException;

  /**
   * Creates a reference expression resolving to the specified class.
   *
   * @param aClass the class to create the reference to.
   * @return the reference expression instance.
   * @throws IncorrectOperationException never (the exception is kept for compatibility purposes).
   */
  @Nonnull
  PsiReferenceExpression createReferenceExpression(@Nonnull PsiClass aClass) throws IncorrectOperationException;

  /**
   * Creates a reference expression resolving to the specified package.
   *
   * @param aPackage the package to create the reference to.
   * @return the reference expression instance.
   * @throws IncorrectOperationException if <code>aPackage</code> is the default (root) package.
   */
  @Nonnull
  PsiReferenceExpression createReferenceExpression(@Nonnull PsiJavaPackage aPackage) throws IncorrectOperationException;

  /**
   * Creates a Java identifier with the specified text.
   *
   * @param text the text of the identifier to create.
   * @return the identifier instance.
   * @throws IncorrectOperationException if <code>text</code> is not a valid Java identifier.
   */
  @Nonnull
  PsiIdentifier createIdentifier(@Nonnull @NonNls String text) throws IncorrectOperationException;

  /**
   * Creates a Java keyword with the specified text.
   *
   * @param keyword the text of the keyword to create.
   * @return the keyword instance.
   * @throws IncorrectOperationException if <code>text</code> is not a valid Java keyword.
   */
  @Nonnull
  PsiKeyword createKeyword(@Nonnull @NonNls String keyword) throws IncorrectOperationException;

  @Nonnull
  PsiKeyword createKeyword(@Nonnull @NonNls String keyword, PsiElement context) throws IncorrectOperationException;

  /**
   * Creates an import statement for importing the specified class.
   *
   * @param aClass the class to create the import statement for.
   * @return the import statement instance.
   * @throws IncorrectOperationException if <code>aClass</code> is an anonymous or local class.
   */
  @Nonnull
  PsiImportStatement createImportStatement(@Nonnull PsiClass aClass) throws IncorrectOperationException;

  /**
   * Creates an on-demand import statement for importing classes from the package with the specified name.
   *
   * @param packageName the name of package to create the import statement for.
   * @return the import statement instance.
   * @throws IncorrectOperationException if <code>packageName</code> is not a valid qualified package name.
   */
  @Nonnull
  PsiImportStatement createImportStatementOnDemand(@Nonnull @NonNls String packageName) throws IncorrectOperationException;

  /**
   * Creates a local variable declaration statement with the specified name, type and initializer,
   * optionally without reformatting the declaration.
   *
   * @param name        the name of the variable to create.
   * @param type        the type of the variable to create.
   * @param initializer the initializer for the variable.
   * @return the variable instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid identifier or
   *                                     <code>type</code> is not a valid type.
   */
  @Nonnull
  PsiDeclarationStatement createVariableDeclarationStatement(@NonNls @Nonnull String name, @Nonnull PsiType type, @Nullable PsiExpression initializer) throws IncorrectOperationException;

  /**
   * Creates a PSI element for the "&#64;param" JavaDoc tag.
   *
   * @param parameterName the name of the parameter for which the tag is created.
   * @param description   the description of the parameter for which the tag is created.
   * @return the created tag.
   * @throws IncorrectOperationException if the name or description are invalid.
   */
  @Nonnull
  PsiDocTag createParamTag(@Nonnull String parameterName, String description) throws IncorrectOperationException;

  /**
   * Returns a synthetic Java class containing methods which are defined on Java arrays.
   *
   * @param languageLevel language level used to construct array class.
   * @return the array synthetic class.
   */
  @Nonnull
  PsiClass getArrayClass(@Nonnull LanguageLevel languageLevel);

  /**
   * Returns the class type for a synthetic Java class containing methods which
   * are defined on Java arrays with the specified element type.
   *
   * @param componentType the component type of the array for which the class type is returned.
   * @param languageLevel language level used to construct array class.
   * @return the class type the array synthetic class.
   */
  @Nonnull
  PsiClassType getArrayClassType(@Nonnull PsiType componentType, @Nonnull final LanguageLevel languageLevel);

  /**
   * Creates a package statement for the specified package name.
   *
   * @param name the name of the package to use in the package statement.
   * @return the created package statement instance.
   * @throws IncorrectOperationException if <code>name</code> is not a valid package name.
   */
  @Nonnull
  PsiPackageStatement createPackageStatement(@Nonnull String name) throws IncorrectOperationException;

  /**
   * Creates an <code>import static</code> statement for importing the specified member
   * from the specified class.
   *
   * @param aClass     the class from which the member is imported.
   * @param memberName the name of the member to import.
   * @return the created statement.
   * @throws IncorrectOperationException if the class is inner or local, or
   *                                     <code>memberName</code> is not a valid identifier.
   */
  @Nonnull
  PsiImportStaticStatement createImportStaticStatement(@Nonnull PsiClass aClass, @Nonnull String memberName) throws IncorrectOperationException;

  /**
   * Creates a parameter list from the specified parameter names and types.
   *
   * @param names the array of names for the parameters to create.
   * @param types the array of types for the parameters to create.
   * @return the created parameter list.
   * @throws IncorrectOperationException if some of the parameter names or types are invalid.
   */
  @Override
  @Nonnull
  PsiParameterList createParameterList(@Nonnull @NonNls String[] names, @Nonnull PsiType[] types) throws IncorrectOperationException;

  /**
   * Creates a reference list element from the specified array of references.
   *
   * @param references the array of references to include in the list.
   * @return the created reference list.
   * @throws IncorrectOperationException if some of the references are invalid.
   */
  @Nonnull
  PsiReferenceList createReferenceList(@Nonnull PsiJavaCodeReferenceElement[] references) throws IncorrectOperationException;

  @Nonnull
  PsiSubstitutor createRawSubstitutor(@Nonnull PsiSubstitutor baseSubstitutor, @Nonnull PsiTypeParameter[] typeParameters);

  /**
   * Create a lightweight PsiElement of given element type in a lightweight non-physical PsiFile (aka DummyHolder) in a given context.
   * Element type's language should have a parser definition which supports parsing for this element type (first
   * parameter in {@link PsiParser#parse(IElementType, PsiBuilder, com.intellij.lang.LanguageVersion)}.
   *
   * @param text    text to parse
   * @param type    node type
   * @param context context
   * @return PsiElement of the desired element type
   */
  @Nonnull
  PsiElement createDummyHolder(@Nonnull String text, @Nonnull IElementType type, @Nullable PsiElement context);

  /**
   * Creates a <code>catch</code> section for catching an exception of the specified
   * type and name.
   *
   * @param exceptionType the type of the exception to catch (either {@linkplain PsiClassType} or {@linkplain PsiDisjunctionType}).
   * @param exceptionName the name of the variable in which the caught exception is stored (may be an empty string).
   * @param context       the context for resolving references.
   * @return the created catch section instance.
   * @throws IncorrectOperationException if some of the parameters are not valid.
   */
  @Nonnull
  PsiCatchSection createCatchSection(@Nonnull PsiType exceptionType, @Nonnull String exceptionName, @Nullable PsiElement context) throws IncorrectOperationException;

  @Override
  @Nonnull
  PsiExpression createExpressionFromText(@Nonnull String text, @Nullable PsiElement context) throws IncorrectOperationException;

  /**
   * Creates a Java type from the specified text.
   *
   * @param text the text of the type to create (a primitive type keyword).
   * @return the created type instance.
   * @throws IncorrectOperationException if some of the parameters are not valid.
   */
  @Nonnull
  PsiType createPrimitiveTypeFromText(@Nonnull String text) throws IncorrectOperationException;
}
