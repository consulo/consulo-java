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
import com.intellij.java.language.jvm.annotation.JvmAnnotationAttribute;
import consulo.annotation.access.RequiredReadAction;
import consulo.java.language.localize.JavaLanguageLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.meta.PsiMetaOwner;
import consulo.localize.LocalizeValue;
import consulo.util.collection.ArrayFactory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.annotation.ElementType;
import java.util.Arrays;
import java.util.List;

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

    String DEFAULT_REFERENCED_METHOD_NAME = "value";

    /**
     * Kinds of element to which an annotation type is applicable (see {@link ElementType}).
     */
    enum TargetType {
        // see java.lang.annotation.ElementType
        TYPE(JavaLanguageLocalize.annotationTargetType()),
        FIELD(JavaLanguageLocalize.annotationTargetField()),
        METHOD(JavaLanguageLocalize.annotationTargetMethod()),
        PARAMETER(JavaLanguageLocalize.annotationTargetParameter()),
        CONSTRUCTOR(JavaLanguageLocalize.annotationTargetConstructor()),
        LOCAL_VARIABLE(JavaLanguageLocalize.annotationTargetLocalVariable()),
        ANNOTATION_TYPE(JavaLanguageLocalize.annotationTargetAnnotationType()),
        PACKAGE(JavaLanguageLocalize.annotationTargetPackage()),
        TYPE_USE(JavaLanguageLocalize.annotationTargetTypeUse()),
        TYPE_PARAMETER(JavaLanguageLocalize.annotationTargetTypeParameter()),
        MODULE(JavaLanguageLocalize.annotationTargetModule()),
        RECORD_COMPONENT(JavaLanguageLocalize.annotationTargetRecordComponent()),
        // auxiliary value, used when it's impossible to determine annotation's targets
        UNKNOWN(LocalizeValue.of("?"));

        public static final TargetType[] EMPTY_ARRAY = {};

        @Nonnull
        private final LocalizeValue myPresentableText;

        TargetType(@Nonnull LocalizeValue presentableText) {
            myPresentableText = presentableText;
        }

        @Nonnull
        public LocalizeValue getPresentableText() {
            return myPresentableText;
        }
    }

    /**
     * Returns the list of parameters for the annotation.
     *
     * @return the parameter list instance.
     */
    @Nonnull
    PsiAnnotationParameterList getParameterList();

    /**
     * Returns the fully qualified name of the annotation class.
     *
     * @return the class name, or null if the annotation is unresolved.
     */
    @Nullable
    @Override
    String getQualifiedName();

    /**
     * Returns the reference element representing the name of the annotation.
     *
     * @return the annotation name element.
     */
    @Nullable
    PsiJavaCodeReferenceElement getNameReferenceElement();

    /**
     * Returns the value of the annotation element with the specified name.
     *
     * @param attributeName name of the annotation element for which the value is requested. If it isn't defined in annotation,
     *                      the default value is returned.
     * @return the element value, or null if the annotation does not contain a value for
     * the element and the element has no default value.
     */
    @Nullable
    PsiAnnotationMemberValue findAttributeValue(@Nullable String attributeName);

    /**
     * Returns the value of the annotation element with the specified name.
     *
     * @param attributeName name of the annotation element for which the value is requested, declared in this annotation.
     * @return the element value, or null if the annotation does not contain a value for
     * the element.
     */
    @Nullable
    PsiAnnotationMemberValue findDeclaredAttributeValue(@Nullable String attributeName);

    /**
     * @see PsiNameValuePair#getDetachedValue()
     */
    @Nullable
    default PsiAnnotationMemberValue findDeclaredAttributeDetachedValue(@Nullable String attributeName) {
        return findDeclaredAttributeValue(attributeName);
    }

    /**
     * Set annotation attribute value. Adds new name-value pair or uses an existing one, expands unnamed 'value' attribute name if needed.
     *
     * @param attributeName attribute name
     * @param value         new value template element
     * @return new declared attribute value
     */
    <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@Nullable String attributeName, @Nullable T value);

    /**
     * Returns an owner of the annotation - usually a parent, but for type annotations the owner might be a type element.
     *
     * @return annotation owner
     */
    @Nullable
    PsiAnnotationOwner getOwner();

    /**
     * @return whether the annotation has the given qualified name. Specific languages may provide efficient implementation
     * that doesn't always create/resolve annotation reference.
     */
    default boolean hasQualifiedName(@Nonnull String qualifiedName) {
        return qualifiedName.equals(getQualifiedName());
    }

    @Nonnull
    @Override
    default List<JvmAnnotationAttribute> getAttributes() {
        return Arrays.asList(getParameterList().getAttributes());
    }

    /**
     * @return the target of {@link #getNameReferenceElement()}, if it's an {@code @interface}, otherwise null
     */
    @Nullable
    @RequiredReadAction
    default PsiClass resolveAnnotationType() {
        PsiJavaCodeReferenceElement element = getNameReferenceElement();
        PsiElement declaration = element == null ? null : element.resolve();
        return declaration instanceof PsiClass annotationClass && annotationClass.isAnnotationType() ? annotationClass : null;
    }
}
