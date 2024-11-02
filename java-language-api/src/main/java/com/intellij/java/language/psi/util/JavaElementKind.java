// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi.util;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiSnippetDocTagBody;
import consulo.java.language.localize.JavaLanguageLocalize;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

/**
 * Represents a kind of element that appears in Java source code.
 * The main purpose of this enum is to be able to display localized element name in UI
 */
public enum JavaElementKind {
    ABSTRACT_METHOD(JavaLanguageLocalize.elementAbstract_method()),
    ANNOTATION(JavaLanguageLocalize.elementAnnotation()),
    ANONYMOUS_CLASS(JavaLanguageLocalize.elementAnonymous_class()),
    CLASS(JavaLanguageLocalize.elementClass()),
    TYPE_PARAMETER(JavaLanguageLocalize.elementTypeParameter()),
    CONSTANT(JavaLanguageLocalize.elementConstant()),
    CONSTRUCTOR(JavaLanguageLocalize.elementConstructor()),
    ENUM(JavaLanguageLocalize.elementEnum()),
    ENUM_CONSTANT(JavaLanguageLocalize.elementEnum_constant()),
    EXPRESSION(JavaLanguageLocalize.elementExpression()),
    FIELD(JavaLanguageLocalize.elementField()),
    INITIALIZER(JavaLanguageLocalize.elementInitializer()),
    INTERFACE(JavaLanguageLocalize.elementInterface()),
    LABEL(JavaLanguageLocalize.elementLabel()),
    LOCAL_VARIABLE(JavaLanguageLocalize.elementLocal_variable()),
    METHOD(JavaLanguageLocalize.elementMethod()),
    MODULE(JavaLanguageLocalize.elementModule()),
    PACKAGE(JavaLanguageLocalize.elementPackage()),
    PARAMETER(JavaLanguageLocalize.elementParameter()),
    PATTERN_VARIABLE(JavaLanguageLocalize.elementPattern_variable()),
    RECORD(JavaLanguageLocalize.elementRecord()),
    RECORD_COMPONENT(JavaLanguageLocalize.elementRecord_component()),
    SNIPPET_BODY(JavaLanguageLocalize.elementSnippet_body()),
    STATEMENT(JavaLanguageLocalize.elementStatement()),
    UNKNOWN(JavaLanguageLocalize.elementUnknown()),
    VARIABLE(JavaLanguageLocalize.elementVariable()),
    THROWS_LIST(JavaLanguageLocalize.elementThrowsList()),
    EXTENDS_LIST(JavaLanguageLocalize.elementExtendsList()),
    RECEIVER_PARAMETER(JavaLanguageLocalize.elementReceiverParameter()),
    METHOD_CALL(JavaLanguageLocalize.elementMethodCall()),
    TYPE_ARGUMENTS(JavaLanguageLocalize.elementTypeArguments()),
    SEMICOLON(JavaLanguageLocalize.elementTypeSemicolon());

    @Nonnull
    private final LocalizeValue myName;

    JavaElementKind(@Nonnull LocalizeValue name) {
        myName = name;
    }

    /**
     * @return human-readable name of the item having the subject role in the sentence (nominative case)
     */
    public LocalizeValue subject() {
        return myName;
    }

    /**
     * @return human-readable name of the item having the object role in the sentence (accusative case)
     */
    @Nonnull
    public LocalizeValue object() {
        return myName;
    }

    /**
     * @return less descriptive type for this type; usually result can be described in a single word
     * (e.g. LOCAL_VARIABLE is replaced with VARIABLE).
     */
    @Nonnull
    public JavaElementKind lessDescriptive() {
        return switch (this) {
            case ABSTRACT_METHOD -> METHOD;
            case LOCAL_VARIABLE, PATTERN_VARIABLE -> VARIABLE;
            case CONSTANT -> FIELD;
            case TYPE_PARAMETER, ANONYMOUS_CLASS -> CLASS;
            default -> this;
        };
    }

    /**
     * @param element element to get the kind from
     * @return resulting kind
     */
    public static JavaElementKind fromElement(@Nonnull PsiElement element) {
        if (element instanceof PsiClass psiClass) {
            if (psiClass instanceof PsiAnonymousClass) {
                return ANONYMOUS_CLASS;
            }
            if (psiClass.isEnum()) {
                return ENUM;
            }
            if (psiClass.isRecord()) {
                return RECORD;
            }
            if (psiClass.isAnnotationType()) {
                return ANNOTATION;
            }
            if (psiClass.isInterface()) {
                return INTERFACE;
            }
            if (psiClass instanceof PsiTypeParameter) {
                return TYPE_PARAMETER;
            }
            return CLASS;
        }
        if (element instanceof PsiMethod method) {
            if (method.isConstructor()) {
                return CONSTRUCTOR;
            }
            if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return ABSTRACT_METHOD;
            }
            return METHOD;
        }
        if (element instanceof PsiField field) {
            if (field instanceof PsiEnumConstant) {
                return ENUM_CONSTANT;
            }
            if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
                return CONSTANT;
            }
            return FIELD;
        }
        if (element instanceof PsiReferenceParameterList) {
            return TYPE_ARGUMENTS;
        }
        if (element instanceof PsiReferenceList referenceList) {
            PsiReferenceList.Role role = referenceList.getRole();
            if (role == PsiReferenceList.Role.THROWS_LIST) {
                return THROWS_LIST;
            }
            if (role == PsiReferenceList.Role.EXTENDS_LIST) {
                return EXTENDS_LIST;
            }
        }
        if (element instanceof PsiAnnotation) {
            return ANNOTATION;
        }
        if (element instanceof PsiRecordComponent) {
            return RECORD_COMPONENT;
        }
        if (element instanceof PsiLocalVariable) {
            return LOCAL_VARIABLE;
        }
        if (element instanceof PsiPatternVariable) {
            return PATTERN_VARIABLE;
        }
        if (element instanceof PsiParameter) {
            return PARAMETER;
        }
        if (element instanceof PsiReceiverParameter) {
            return RECEIVER_PARAMETER;
        }
        if (element instanceof PsiVariable) {
            return VARIABLE;
        }
        if (element instanceof PsiJavaPackage) {
            return PACKAGE;
        }
        if (element instanceof PsiJavaModule) {
            return MODULE;
        }
        if (element instanceof PsiClassInitializer) {
            return INITIALIZER;
        }
        if (element instanceof PsiLabeledStatement) {
            return LABEL;
        }
        if (element instanceof PsiStatement) {
            return STATEMENT;
        }
        if (element instanceof PsiMethodCallExpression) {
            return METHOD_CALL;
        }
        if (element instanceof PsiExpression) {
            return EXPRESSION;
        }
        if (element instanceof PsiSnippetDocTagBody) {
            return SNIPPET_BODY;
        }
        if (PsiUtil.isJavaToken(element, JavaTokenType.SEMICOLON)) {
            return SEMICOLON;
        }
        return UNKNOWN;
    }
}
