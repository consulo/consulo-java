// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.ig.junit;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

import java.util.Arrays;

import static com.intellij.java.language.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

@ExtensionImpl
public class BeforeClassOrAfterClassIsPublicStaticVoidNoArgInspectionBase extends BaseInspection {
    private static final String[] STATIC_CONFIGS = {
        "org.junit.BeforeClass",
        "org.junit.AfterClass",
        "org.junit.jupiter.api.BeforeAll",
        "org.junit.jupiter.api.AfterAll"
    };

    protected static boolean isJunit4Annotation(String annotation) {
        return annotation.endsWith("Class");
    }

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "BeforeOrAfterWithIncorrectSignature";
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.beforeClassOrAfterClassIsPublicStaticVoidNoArgDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.beforeClassOrAfterClassIsPublicStaticVoidNoArgProblemDescriptor(infos[1]).get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new BeforeClassOrAfterClassIsPublicStaticVoidNoArgVisitor();
    }

    private static class BeforeClassOrAfterClassIsPublicStaticVoidNoArgVisitor extends BaseInspectionVisitor {
        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            //note: no call to super;
            String annotation = Arrays.stream(STATIC_CONFIGS)
                .filter(anno -> AnnotationUtil.isAnnotated(method, anno, CHECK_HIERARCHY))
                .findFirst()
                .orElse(null);
            if (annotation == null) {
                return;
            }
            PsiType returnType = method.getReturnType();
            if (returnType == null) {
                return;
            }
            PsiClass targetClass = method.getContainingClass();
            if (targetClass == null) {
                return;
            }

            PsiParameterList parameterList = method.getParameterList();
            boolean junit4Annotation = isJunit4Annotation(annotation);
            if (junit4Annotation && (parameterList.getParametersCount() != 0 || !method.isPublic())
                || !PsiType.VOID.equals(returnType)
                || !method.isStatic() && (junit4Annotation || !TestUtils.testInstancePerClass(targetClass))) {
                registerMethodError(method, method, annotation);
            }
        }
    }
}
