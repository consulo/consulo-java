/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection.concurrencyAnnotations;

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.analysis.impl.codeInspection.concurrencyAnnotations.JCiPUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class NonFinalGuardInspection extends BaseJavaLocalInspectionTool {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesConcurrencyAnnotationIssues();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return JavaAnalysisLocalize.inspectionNonFinalGuardDisplayName();
    }

    @Override
    @Nonnull
    public String getShortName() {
        return "NonFinalGuard";
    }


    @Override
    @Nonnull
    public PsiElementVisitor buildVisitorImpl(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        Object state
    ) {
        return new Visitor(holder);
    }

    private static class Visitor extends JavaElementVisitor {
        private final ProblemsHolder myHolder;

        public Visitor(ProblemsHolder holder) {
            myHolder = holder;
        }

        @Override
        @RequiredReadAction
        public void visitAnnotation(@Nonnull PsiAnnotation annotation) {
            super.visitAnnotation(annotation);
            if (!JCiPUtil.isGuardedByAnnotation(annotation)) {
                return;
            }
            String guardValue = JCiPUtil.getGuardValue(annotation);
            if (guardValue == null || "this".equals(guardValue)) {
                return;
            }
            PsiClass containingClass = PsiTreeUtil.getParentOfType(annotation, PsiClass.class);
            if (containingClass == null) {
                return;
            }
            PsiField guardField = containingClass.findFieldByName(guardValue, true);
            if (guardField == null) {
                return;
            }
            if (guardField.isFinal()) {
                return;
            }
            PsiAnnotationMemberValue member = annotation.findAttributeValue("value");
            if (member == null) {
                return;
            }
            myHolder.newProblem(JavaAnalysisLocalize.nonFinalGuardedByFieldRefLoc())
                .range(member)
                .create();
        }

        @Override
        @RequiredReadAction
        public void visitDocTag(@Nonnull PsiDocTag psiDocTag) {
            super.visitDocTag(psiDocTag);
            if (!JCiPUtil.isGuardedByTag(psiDocTag)) {
                return;
            }
            String guardValue = JCiPUtil.getGuardValue(psiDocTag);
            if ("this".equals(guardValue)) {
                return;
            }
            PsiClass containingClass = PsiTreeUtil.getParentOfType(psiDocTag, PsiClass.class);
            if (containingClass == null) {
                return;
            }
            PsiField guardField = containingClass.findFieldByName(guardValue, true);
            if (guardField == null || guardField.isFinal()) {
                return;
            }
            myHolder.newProblem(JavaAnalysisLocalize.nonFinalGuardedByField0Loc(guardValue))
                .range(psiDocTag)
                .create();
        }
    }
}