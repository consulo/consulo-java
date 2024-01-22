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

import com.intellij.java.language.jvm.JvmAnnotation;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.meta.PsiMetaOwner;
import consulo.util.collection.ArrayFactory;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;
import java.lang.annotation.ElementType;

/**
 * Represents a Java annotation.
 *
 * @author ven
 */
public interface PsiAnnotation extends PsiAnnotationMemberValue, PsiMetaOwner, JvmAnnotation {
  /**
   * The empty array of PSI annotations which can be reused to avoid unnecessary allocations.
   */
  PsiAnnotation[] EMPTY_ARRAY = new PsiAnnotation[0];

  ArrayFactory<PsiAnnotation> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiAnnotation[count];

  @NonNls
  String DEFAULT_REFERENCED_METHOD_NAME = "value";

  /**
   * Kinds of element to which an annotation type is applicable (see {@link ElementType}).
   */
  enum TargetType {
    // see java.lang.annotation.ElementType
    TYPE,
    FIELD,
    METHOD,
    PARAMETER,
    CONSTRUCTOR,
    LOCAL_VARIABLE,
    ANNOTATION_TYPE,
    PACKAGE,
    TYPE_USE,
    TYPE_PARAMETER,
    MODULE,
    RECORD_COMPONENT,
    // auxiliary value, used when it's impossible to determine annotation's targets
    UNKNOWN;

    public static final TargetType[] EMPTY_ARRAY = {};
  }

  /**
   * Returns the list of parameters for the annotation.
   *
   * @return the parameter list instance.
   */
  @jakarta.annotation.Nonnull
  PsiAnnotationParameterList getParameterList();

  /**
   * Returns the fully qualified name of the annotation class.
   *
   * @return the class name, or null if the annotation is unresolved.
   */
  @jakarta.annotation.Nullable
  @NonNls
  String getQualifiedName();

  /**
   * Returns the reference element representing the name of the annotation.
   *
   * @return the annotation name element.
   */
  @jakarta.annotation.Nullable
  PsiJavaCodeReferenceElement getNameReferenceElement();

  /**
   * Returns the value of the annotation element with the specified name.
   *
   * @param attributeName name of the annotation element for which the value is requested. If it isn't defined in annotation,
   *                      the default value is returned.
   * @return the element value, or null if the annotation does not contain a value for
   * the element and the element has no default value.
   */
  @jakarta.annotation.Nullable
  PsiAnnotationMemberValue findAttributeValue(@jakarta.annotation.Nullable @NonNls String attributeName);

  /**
   * Returns the value of the annotation element with the specified name.
   *
   * @param attributeName name of the annotation element for which the value is requested, declared in this annotation.
   * @return the element value, or null if the annotation does not contain a value for
   * the element.
   */
  @jakarta.annotation.Nullable
  PsiAnnotationMemberValue findDeclaredAttributeValue(@jakarta.annotation.Nullable @NonNls String attributeName);

  /**
   * Set annotation attribute value. Adds new name-value pair or uses an existing one, expands unnamed 'value' attribute name if needed.
   *
   * @param attributeName attribute name
   * @param value         new value template element
   * @return new declared attribute value
   */
  <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@Nullable @NonNls String attributeName, @jakarta.annotation.Nullable T value);

  /**
   * Returns an owner of the annotation - usually a parent, but for type annotations the owner might be a type element.
   *
   * @return annotation owner
   */
  @jakarta.annotation.Nullable
  PsiAnnotationOwner getOwner();

  /**
   * @return whether the annotation has the given qualified name. Specific languages may provide efficient implementation
   * that doesn't always create/resolve annotation reference.
   */
  default boolean hasQualifiedName(@jakarta.annotation.Nonnull String qualifiedName) {
    return qualifiedName.equals(getQualifiedName());
  }

  @Nonnull
  @Override
  default PsiElement getSourceElement() {
    return this;
  }

  @Override
  default void navigate(boolean requestFocus) {
  }

  @Override
  default boolean canNavigate() {
    return false;
  }

  @Override
  default boolean canNavigateToSource() {
    return false;
  }

  /**
   * @return the target of {@link #getNameReferenceElement()}, if it's an {@code @interface}, otherwise null
   */
  @jakarta.annotation.Nullable
  @RequiredReadAction
  default PsiClass resolveAnnotationType() {
    PsiJavaCodeReferenceElement element = getNameReferenceElement();
    PsiElement declaration = element == null ? null : element.resolve();
    if (!(declaration instanceof PsiClass) || !((PsiClass)declaration).isAnnotationType()) return null;
    return (PsiClass)declaration;
  }
}
