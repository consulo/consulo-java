// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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
package com.intellij.java.analysis.impl.codeInspection.nullable;

import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullabilityAnnotationInfo;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressManager;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AnnotateOverriddenMethodParameterFix implements LocalQuickFix {
    private final Nullability myTargetNullability;

    AnnotateOverriddenMethodParameterFix(Nullability targetNullability) {
        myTargetNullability = targetNullability;
    }

    @Override
    public LocalizeValue getName() {
        return JavaAnalysisLocalize.annotateOverriddenMethodsParameters(
            myTargetNullability == Nullability.NOT_NULL ? "NotNull" : "Nullable"
        );
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        List<PsiParameter> toAnnotate = new ArrayList<>();

        PsiParameter parameter = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiParameter.class, false);
        if (parameter == null || !processParameterInheritorsUnderProgress(parameter, toAnnotate::add)) {
            return;
        }

        NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
        FileModificationService.getInstance().preparePsiElementsForWrite(toAnnotate);
        for (PsiParameter psiParam : toAnnotate) {
            assert psiParam != null : toAnnotate;
            NullabilityAnnotationInfo info = manager.findEffectiveNullabilityInfo(psiParam);
            if (info != null && info.getNullability() == myTargetNullability &&
                info.getInheritedFrom() == null && !info.isInferred()) {
                continue;
            }
            AddAnnotationPsiFix fix = myTargetNullability == Nullability.NOT_NULL
                ? AddAnnotationPsiFix.createAddNotNullFix(psiParam)
                : AddAnnotationPsiFix.createAddNullableFix(psiParam);
            if (fix != null) {
                fix.invoke(project, psiParam.getContainingFile(), psiParam, psiParam);
            }
        }
    }

    public static boolean processParameterInheritorsUnderProgress(PsiParameter parameter, Consumer<? super PsiParameter> consumer) {
        PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);
        if (method == null) return false;
        PsiParameter[] parameters = method.getParameterList().getParameters();
        int index = ArrayUtil.find(parameters, parameter);

        return processModifiableInheritorsUnderProgress(method, psiMethod -> {
            PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
            if (index < psiParameters.length) {
                consumer.accept(psiParameters[index]);
            }
        });
    }

    public static boolean processModifiableInheritorsUnderProgress(PsiMethod method, Consumer<? super PsiMethod> consumer) {
        return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            for (PsiMethod psiMethod : OverridingMethodsSearch.search(method).findAll()) {
                ReadAction.run(() -> {
                    if (psiMethod.isPhysical() && !NullableStuffInspectionBase.shouldSkipOverriderAsGenerated(psiMethod)) {
                        consumer.accept(psiMethod);
                    }
                });
            }
        }, JavaAnalysisLocalize.searchingForOverridingMethods(), true, method.getProject());
    }
}
