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
package com.intellij.java.impl.codeInspection.nullable;

import com.intellij.java.analysis.impl.codeInspection.nullable.NullableStuffInspectionBase;
import com.intellij.java.analysis.impl.psi.impl.search.JavaNullMethodArgumentUtil;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessRule;
import consulo.ide.impl.find.PsiElement2UsageTargetAdapter;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.usage.*;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class NullableStuffInspection extends NullableStuffInspectionBase {
    @Override
    protected LocalQuickFix createNavigateToNullParameterUsagesFix(PsiParameter parameter) {
        return new NavigateToNullLiteralArguments(parameter);
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    public static class NavigateToNullLiteralArguments extends LocalQuickFixOnPsiElement {
        public NavigateToNullLiteralArguments(@Nonnull PsiParameter element) {
            super(element);
        }

        @Nonnull
        @Override
        public LocalizeValue getText() {
            return JavaInspectionsLocalize.nullableStuffInspectionNavigateNullArgumentUsagesFixFamilyName();
        }

        @Override
        @RequiredReadAction
        public void invoke(
            @Nonnull Project project,
            @Nonnull PsiFile file,
            @Nonnull PsiElement startElement,
            @Nonnull PsiElement endElement
        ) {
            PsiParameter p = (PsiParameter)startElement;
            PsiMethod method = PsiTreeUtil.getParentOfType(p, PsiMethod.class);
            if (method == null) {
                return;
            }
            int parameterIdx = ArrayUtil.find(method.getParameterList().getParameters(), p);
            if (parameterIdx < 0) {
                return;
            }

            UsageViewPresentation presentation = new UsageViewPresentation();
            String title = JavaInspectionsLocalize.nullableStuffInspectionNavigateNullArgumentUsagesViewName(p.getName()).get();
            presentation.setUsagesString(title);
            presentation.setTabName(title);
            presentation.setTabText(title);
            UsageViewManager.getInstance(project).searchAndShowUsages(
                new UsageTarget[]{new PsiElement2UsageTargetAdapter(method.getParameterList().getParameters()[parameterIdx])},
                () -> processor -> AccessRule.read(() -> JavaNullMethodArgumentUtil.searchNullArgument(
                    method,
                    parameterIdx,
                    (arg) -> processor.test(new UsageInfo2UsageAdapter(new UsageInfo(arg)))
                )),
                false,
                false,
                presentation,
                null
            );
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }
    }
}
