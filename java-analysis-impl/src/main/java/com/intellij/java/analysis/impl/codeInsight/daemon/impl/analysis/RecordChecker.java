// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInsight.daemon.impl.analysis;

import com.intellij.java.language.impl.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.java.language.impl.psi.impl.light.LightRecordMethod;
import com.intellij.java.language.impl.psi.util.AccessModifier;
import com.intellij.java.language.impl.psi.util.JavaPsiRecordUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.java.language.util.JavaPsiConstructorUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.java.language.localize.JavaCompilationErrorLocalize;
import consulo.language.editor.rawHighlight.HighlightInfo;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

final class RecordChecker {
    private RecordChecker() {
    }

    @RequiredReadAction
    static HighlightInfo.@Nullable Builder checkRecordHeader(PsiClass psiClass) {
        PsiRecordHeader header = psiClass.getRecordHeader();
        if (!psiClass.isRecord()) {
            if (header != null) {
                return error(header.getTextRange(), JavaCompilationErrorLocalize.recordHeaderRegularClass());
            }
            return null;
        }
        PsiIdentifier identifier = psiClass.getNameIdentifier();
        if (identifier == null) {
            return null;
        }
        if (header == null) {
            return error(identifier.getTextRange(), JavaCompilationErrorLocalize.recordNoHeader());
        }
        return null;
    }

    @RequiredReadAction
    static HighlightInfo.@Nullable Builder checkRecordComponentWellFormed(PsiRecordComponent component) {
        if (component.isVarArgs() && PsiTreeUtil.getNextSiblingOfType(component, PsiRecordComponent.class) != null) {
            return error(component.getTextRange(), JavaCompilationErrorLocalize.recordComponentVarargNotLast());
        }
        TextRange range = HighlightMethodUtil.getCStyleDeclarationRange(component);
        if (range != null) {
            return error(
                range.shiftRight(component.getTextRange().getStartOffset()),
                JavaCompilationErrorLocalize.recordComponentCstyleDeclaration()
            );
        }
        PsiIdentifier identifier = component.getNameIdentifier();
        if (identifier != null && JavaPsiRecordUtil.ILLEGAL_RECORD_COMPONENT_NAMES.contains(identifier.getText())) {
            return error(identifier.getTextRange(), JavaCompilationErrorLocalize.recordComponentRestrictedName(component.getName()));
        }
        return null;
    }

    @RequiredReadAction
    static HighlightInfo.@Nullable Builder checkRecordAccessorReturnType(PsiRecordComponent component) {
        String componentName = component.getName();
        PsiTypeElement typeElement = component.getTypeElement();
        if (typeElement == null) {
            return null;
        }
        PsiClass containingClass = component.getContainingClass();
        if (containingClass == null) {
            return null;
        }
        PsiMethod[] methods = containingClass.findMethodsByName(componentName, false);
        for (PsiMethod method : methods) {
            if (method instanceof LightRecordMethod) {
                List<HierarchicalMethodSignature> superSignatures =
                    PsiSuperMethodImplUtil.getHierarchicalMethodSignature(method).getSuperSignatures();
                MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
                HighlightInfo.Builder builder =
                    HighlightMethodUtil.checkMethodIncompatibleReturnType(signature, superSignatures, true, component.getTextRange());
                if (builder != null) {
                    return builder;
                }
            }
        }
        return null;
    }

    @RequiredReadAction
    static HighlightInfo.@Nullable Builder checkRecordAccessorDeclaration(PsiMethod method) {
        PsiRecordComponent component = JavaPsiRecordUtil.getRecordComponentForAccessor(method);
        if (component == null) {
            return null;
        }
        PsiIdentifier identifier = method.getNameIdentifier();
        if (identifier == null) {
            return null;
        }
        PsiType componentType = component.getType();
        PsiType methodType = method.getReturnType();
        if (methodType == null) {
            return null; // Either constructor or incorrect method, will be reported in another way
        }
        if (componentType instanceof PsiEllipsisType ellipsisType) {
            componentType = ellipsisType.getComponentType().createArrayType();
        }
        if (!componentType.equals(methodType)) {
            return error(
                requireNonNull(method.getReturnTypeElement()).getTextRange(),
                JavaCompilationErrorLocalize.recordAccessorWrongReturnType(
                    componentType.getPresentableText(),
                    methodType.getPresentableText()
                )
            );
        }
        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return error(identifier.getTextRange(), JavaCompilationErrorLocalize.recordAccessorNonPublic());
        }
        return checkRecordSpecialMethodDeclaration(method);
    }

    @RequiredReadAction
    private static HighlightInfo.@Nullable Builder checkRecordSpecialMethodDeclaration(PsiMethod method) {
        PsiIdentifier identifier = method.getNameIdentifier();
        if (identifier == null) {
            return null;
        }
        PsiTypeParameterList typeParameterList = method.getTypeParameterList();
        if (typeParameterList != null && typeParameterList.getTypeParameters().length > 0) {
            return error(
                typeParameterList.getTextRange(),
                JavaCompilationErrorLocalize.recordSpecialMethodTypeParameters(getRecordMethodKind(method))
            );
        }
        if (method.isConstructor()) {
            AccessModifier modifier = AccessModifier.fromModifierList(method.getModifierList());
            PsiModifierList classModifierList = requireNonNull(method.getContainingClass()).getModifierList();
            if (classModifierList != null) {
                AccessModifier classModifier = AccessModifier.fromModifierList(classModifierList);
                if (classModifier.isWeaker(modifier)) {
                    return error(
                        identifier.getTextRange(),
                        JavaCompilationErrorLocalize.recordConstructorStrongerAccess(getRecordMethodKind(method), classModifier)
                    );
                }
            }
        }
        PsiReferenceList throwsList = method.getThrowsList();
        if (throwsList.getReferenceElements().length > 0) {
            return error(
                throwsList.getFirstChild().getTextRange(),
                JavaCompilationErrorLocalize.recordSpecialMethodThrows(getRecordMethodKind(method))
            );
        }
        return null;
    }

    @RequiredReadAction
    static HighlightInfo.@Nullable Builder checkRecordConstructorDeclaration(PsiMethod method) {
        if (!method.isConstructor()) {
            return null;
        }
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
            return null;
        }
        PsiIdentifier identifier = method.getNameIdentifier();
        if (identifier == null) {
            return null;
        }
        if (!aClass.isRecord()) {
            if (JavaPsiRecordUtil.isCompactConstructor(method)) {
                return error(identifier.getTextRange(), JavaCompilationErrorLocalize.methodNoParameterList());
            }
            return null;
        }
        if (JavaPsiRecordUtil.isExplicitCanonicalConstructor(method)) {
            PsiParameter[] parameters = method.getParameterList().getParameters();
            PsiRecordComponent[] components = aClass.getRecordComponents();
            assert parameters.length == components.length;
            for (int i = 0; i < parameters.length; i++) {
                PsiRecordComponent component = components[i];
                PsiParameter parameter = parameters[i];
                if (!parameter.getType().equals(component.getType())) {
                    return error(
                        requireNonNull(parameter.getTypeElement()).getTextRange(),
                        JavaCompilationErrorLocalize.recordCanonicalConstructorWrongParameterType(
                            component.getName(),
                            component.getType().getPresentableText(),
                            parameter.getType().getPresentableText()
                        )
                    );
                }
                else if (!parameter.getName().equals(component.getName())) {
                    return error(
                        requireNonNull(parameter.getNameIdentifier()).getTextRange(),
                        JavaCompilationErrorLocalize.recordCanonicalConstructorWrongParameterName(component.getName(), parameter.getName())
                    );
                }
            }
            return checkRecordSpecialMethodDeclaration(method);
        }
        if (JavaPsiRecordUtil.isCompactConstructor(method)) {
            return checkRecordSpecialMethodDeclaration(method);
        }
        // Non-canonical constructor
        PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
        if (call == null || JavaPsiConstructorUtil.isSuperConstructorCall(call)) {
            return error(identifier.getTextRange(), JavaCompilationErrorLocalize.recordNoConstructorCallInNonCanonical());
        }
        return null;
    }

    static LocalizeValue getRecordMethodKind(PsiMethod method) {
        if (JavaPsiRecordUtil.isCompactConstructor(method)) {
            return JavaCompilationErrorLocalize.recordCompactConstructor();
        }
        if (JavaPsiRecordUtil.isCanonicalConstructor(method)) {
            return JavaCompilationErrorLocalize.recordCanonicalConstructor();
        }
        if (JavaPsiRecordUtil.getRecordComponentForAccessor(method) != null) {
            return JavaCompilationErrorLocalize.recordAccessor();
        }
        throw new IllegalArgumentException("Record special method expected: " + method);
    }

    private static HighlightInfo.Builder error(TextRange range, LocalizeValue message) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(message);
    }
}
