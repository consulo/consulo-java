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
    ABSTRACT_METHOD(JavaLanguageLocalize.elementAbstractMethodNominative(), JavaLanguageLocalize.elementAbstractMethodAccusative()),
    ANNOTATION(JavaLanguageLocalize.elementAnnotationNominative(), JavaLanguageLocalize.elementAnnotationAccusative()),
    ANONYMOUS_CLASS(JavaLanguageLocalize.elementAnonymousClassNominative(), JavaLanguageLocalize.elementAnonymousClassAccusative()),
    CLASS(JavaLanguageLocalize.elementClassNominative(), JavaLanguageLocalize.elementClassAccusative()),
    TYPE_PARAMETER(JavaLanguageLocalize.elementTypeParameterNominative(), JavaLanguageLocalize.elementTypeParameterAccusative()),
    CONSTANT(JavaLanguageLocalize.elementConstantNominative(), JavaLanguageLocalize.elementConstantAccusative()),
    CONSTRUCTOR(JavaLanguageLocalize.elementConstructorNominative(), JavaLanguageLocalize.elementConstructorAccusative()),
    ENUM(JavaLanguageLocalize.elementEnumNominative(), JavaLanguageLocalize.elementEnumAccusative()),
    ENUM_CONSTANT(JavaLanguageLocalize.elementEnumConstantNominative(), JavaLanguageLocalize.elementEnumConstantAccusative()),
    EXPRESSION(JavaLanguageLocalize.elementExpressionNominative(), JavaLanguageLocalize.elementExpressionAccusative()),
    FIELD(JavaLanguageLocalize.elementFieldNominative(), JavaLanguageLocalize.elementFieldAccusative()),
    INITIALIZER(JavaLanguageLocalize.elementInitializerNominative(), JavaLanguageLocalize.elementInitializerAccusative()),
    INTERFACE(JavaLanguageLocalize.elementInterfaceNominative(), JavaLanguageLocalize.elementInterfaceAccusative()),
    LABEL(JavaLanguageLocalize.elementLabelNominative(), JavaLanguageLocalize.elementLabelAccusative()),
    LOCAL_VARIABLE(JavaLanguageLocalize.elementLocalVariableNominative(), JavaLanguageLocalize.elementLocalVariableAccusative()),
    METHOD(JavaLanguageLocalize.elementMethodNominative(), JavaLanguageLocalize.elementMethodAccusative()),
    MODULE(JavaLanguageLocalize.elementModuleNominative(), JavaLanguageLocalize.elementModuleAccusative()),
    PACKAGE(JavaLanguageLocalize.elementPackageNominative(), JavaLanguageLocalize.elementPackageAccusative()),
    PARAMETER(JavaLanguageLocalize.elementParameterNominative(), JavaLanguageLocalize.elementParameterAccusative()),
    PATTERN_VARIABLE(JavaLanguageLocalize.elementPatternVariableNominative(), JavaLanguageLocalize.elementPatternVariableAccusative()),
    RECORD(JavaLanguageLocalize.elementRecordNominative(), JavaLanguageLocalize.elementRecordAccusative()),
    RECORD_COMPONENT(JavaLanguageLocalize.elementRecordComponentNominative(), JavaLanguageLocalize.elementRecordComponentAccusative()),
    SNIPPET_BODY(JavaLanguageLocalize.elementSnippetBodyNominative(), JavaLanguageLocalize.elementSnippetBodyAccusative()),
    STATEMENT(JavaLanguageLocalize.elementStatementNominative(), JavaLanguageLocalize.elementStatementAccusative()),
    UNKNOWN(JavaLanguageLocalize.elementUnknownNominative(), JavaLanguageLocalize.elementUnknownAccusative()),
    VARIABLE(JavaLanguageLocalize.elementVariableNominative(), JavaLanguageLocalize.elementVariableAccusative()),
    THROWS_LIST(JavaLanguageLocalize.elementThrowsListNominative(), JavaLanguageLocalize.elementThrowsListAccusative()),
    EXTENDS_LIST(JavaLanguageLocalize.elementExtendsListNominative(), JavaLanguageLocalize.elementExtendsListAccusative()),
    RECEIVER_PARAMETER(JavaLanguageLocalize.elementReceiverParameterNominative(), JavaLanguageLocalize.elementReceiverParameterAccusative()),
    METHOD_CALL(JavaLanguageLocalize.elementMethodCallNominative(), JavaLanguageLocalize.elementMethodCallAccusative()),
    TYPE_ARGUMENTS(JavaLanguageLocalize.elementTypeArgumentsNominative(), JavaLanguageLocalize.elementTypeArgumentsAccusative()),
    SEMICOLON(JavaLanguageLocalize.elementTypeSemicolonNominative(), JavaLanguageLocalize.elementTypeSemicolonAccusative());

    @Nonnull
    private final LocalizeValue myNameNominative;
    @Nonnull
    private final LocalizeValue myNameAccusative;

    JavaElementKind(@Nonnull LocalizeValue nameNominative, @Nonnull LocalizeValue nameAccusative) {
        myNameNominative = nameNominative;
        myNameAccusative = nameAccusative;
    }

    /**
     * @return human-readable name of the item having the subject role in the sentence (nominative case)
     */
    public LocalizeValue subject() {
        return myNameNominative;
    }

    /**
     * @return human-readable name of the item having the object role in the sentence (accusative case)
     */
    @Nonnull
    public LocalizeValue object() {
        return myNameAccusative;
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
            if (method.isAbstract()) {
                return ABSTRACT_METHOD;
            }
            return METHOD;
        }
        if (element instanceof PsiField field) {
            if (field instanceof PsiEnumConstant) {
                return ENUM_CONSTANT;
            }
            if (field.isStatic() && field.isFinal()) {
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
