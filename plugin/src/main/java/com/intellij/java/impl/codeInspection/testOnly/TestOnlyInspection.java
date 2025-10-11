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
package com.intellij.java.impl.codeInspection.testOnly;

import com.intellij.java.analysis.impl.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiCallExpression;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class TestOnlyInspection extends BaseJavaLocalInspectionTool {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionLocalize.inspectionTestOnlyProblemsDisplayName();
    }

    @Override
    @Nonnull
    public String getShortName() {
        return "TestOnlyProblems";
    }

    @Override
    @Nonnull
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.inspectionGeneralToolsGroupName();
    }

    @Override
    @Nonnull
    public PsiElementVisitor buildVisitorImpl(
        @Nonnull final ProblemsHolder h,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        Object state
    ) {
        return new JavaElementVisitor() {
            @Override
            public void visitCallExpression(@Nonnull PsiCallExpression e) {
                validate(e, h);
            }
        };
    }

    private void validate(PsiCallExpression e, ProblemsHolder h) {
        if (!isTestOnlyMethodCalled(e)) {
            return;
        }
        if (isInsideTestOnlyMethod(e)) {
            return;
        }
        if (isInsideTestClass(e)) {
            return;
        }
        if (isUnderTestSources(e)) {
            return;
        }

        reportProblem(e, h);
    }

    private boolean isTestOnlyMethodCalled(PsiCallExpression e) {
        return isAnnotatedAsTestOnly(e.resolveMethod());
    }

    private boolean isInsideTestOnlyMethod(PsiCallExpression e) {
        PsiMethod m = getTopLevelParentOfType(e, PsiMethod.class);
        return isAnnotatedAsTestOnly(m);
    }

    private static boolean isAnnotatedAsTestOnly(@Nullable PsiMethod m) {
        if (m == null) {
            return false;
        }
        return AnnotationUtil.isAnnotated(m, AnnotationUtil.TEST_ONLY, false, false) ||
            AnnotationUtil.isAnnotated(m, "com.google.common.annotations.VisibleForTesting", false, false);
    }

    private boolean isInsideTestClass(PsiCallExpression e) {
        PsiClass c = getTopLevelParentOfType(e, PsiClass.class);
        return c != null && TestFrameworks.getInstance().isTestClass(c);
    }

    private <T extends PsiElement> T getTopLevelParentOfType(PsiElement e, Class<T> c) {
        T parent = PsiTreeUtil.getParentOfType(e, c);
        if (parent == null) {
            return null;
        }

        do {
            T next = PsiTreeUtil.getParentOfType(parent, c);
            if (next == null) {
                return parent;
            }
            parent = next;
        }
        while (true);
    }

    private boolean isUnderTestSources(PsiCallExpression e) {
        ProjectRootManager rm = ProjectRootManager.getInstance(e.getProject());
        VirtualFile f = e.getContainingFile().getVirtualFile();
        return f != null && rm.getFileIndex().isInTestSourceContent(f);
    }

    private void reportProblem(PsiCallExpression e, ProblemsHolder h) {
        LocalizeValue message = InspectionLocalize.inspectionTestOnlyProblemsTestOnlyMethodCall();
        h.registerProblem(e, message.get(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
}
