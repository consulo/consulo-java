// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.codeInspection;

import com.intellij.java.analysis.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.component.extension.ExtensionPoint;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
import consulo.java.localize.JavaLocalize;
import consulo.language.editor.inspection.*;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Dmitry Avdeev
 */
@ExtensionImpl
public final class DefaultAnnotationParamInspection extends AbstractBaseJavaLocalInspectionTool<Object> {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return JavaLocalize.inspectionDefaultAnnotationParam();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesDeclarationRedundancy();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "DefaultAnnotationParam";
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitorImpl(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        Object o
    ) {
        return new JavaElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitNameValuePair(@Nonnull PsiNameValuePair pair) {
                PsiAnnotationMemberValue value = pair.getValue();
                PsiReference reference = pair.getReference();
                if (!(reference != null && reference.resolve() instanceof PsiAnnotationMethod annotationMethod)) {
                    return;
                }

                PsiAnnotationMemberValue defaultValue = annotationMethod.getDefaultValue();
                if (defaultValue == null) {
                    return;
                }

                if (AnnotationUtil.equal(value, defaultValue)) {
                    if (annotationMethod.getParent() instanceof PsiClass psiClass) {
                        String qualifiedName = psiClass.getQualifiedName();
                        String name = annotationMethod.getName();
                        ExtensionPoint<DefaultAnnotationParamIgnoreFilter> filters =
                            psiClass.getApplication().getExtensionPoint(DefaultAnnotationParamIgnoreFilter.class);
                        if (filters.anyMatchSafe(ext -> ext.ignoreAnnotationParam(qualifiedName, name))) {
                            return;
                        }
                    }
                    holder.newProblem(JavaInspectionsLocalize.inspectionMessageRedundantDefaultParameterValueAssignment())
                        .range(value)
                        .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                        .withFix(createRemoveParameterFix(value))
                        .create();
                }
            }
        };
    }

    @Nonnull
    private static LocalQuickFix createRemoveParameterFix(PsiAnnotationMemberValue value) {
        return new LocalQuickFixAndIntentionActionOnPsiElement(value) {
            @Nonnull
            @Override
            public LocalizeValue getText() {
                return JavaLocalize.quickfixFamilyRemoveRedundantParameter();
            }

            @Override
            @RequiredWriteAction
            public void invoke(
                @Nonnull Project project,
                @Nonnull PsiFile psiFile,
                @Nullable Editor editor,
                @Nonnull PsiElement startElement,
                @Nonnull PsiElement endElement
            ) {
                startElement.getParent().delete();
            }
        };
    }
}
