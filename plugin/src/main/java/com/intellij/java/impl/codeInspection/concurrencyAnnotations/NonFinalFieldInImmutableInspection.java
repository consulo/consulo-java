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
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;

@ExtensionImpl
public class NonFinalFieldInImmutableInspection extends BaseJavaLocalInspectionTool {
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.groupNamesConcurrencyAnnotationIssues();
    }

    @Override
    public LocalizeValue getDisplayName() {
        return JavaAnalysisLocalize.inspectionNonFinalFieldInImmutableDisplayName();
    }

    @Override
    public String getShortName() {
        return "NonFinalFieldInImmutable";
    }

    @Override
    public PsiElementVisitor buildVisitorImpl(
        final ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        Object state
    ) {
        return new JavaElementVisitor() {
            @Override
            public void visitField(PsiField field) {
                super.visitField(field);
                if (field.isFinal()) {
                    return;
                }
                PsiClass containingClass = field.getContainingClass();
                if (containingClass != null) {
                    if (!JCiPUtil.isImmutable(containingClass)) {
                        return;
                    }
                    holder.newProblem(JavaAnalysisLocalize.nonFinalFieldCodeRefCodeInImmutableClassLoc())
                        .range(field)
                        .create();
                }
            }
        };
    }
}