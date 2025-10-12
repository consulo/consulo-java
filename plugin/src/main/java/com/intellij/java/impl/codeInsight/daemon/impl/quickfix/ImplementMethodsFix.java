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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.impl.codeInsight.generation.PsiMethodMember;
import com.intellij.java.language.impl.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.infos.CandidateInfo;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.ide.impl.idea.ide.util.MemberChooser;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.util.ProductivityFeatureNames;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.List;

public class ImplementMethodsFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    public ImplementMethodsFix(PsiElement aClass) {
        super(aClass);
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return JavaQuickFixLocalize.implementMethodsFix();
    }

    @Override
    public boolean isAvailable(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        PsiElement myPsiElement = startElement;
        return myPsiElement.isValid() && myPsiElement.getManager().isInProject(myPsiElement);
    }

    @Override
    public void invoke(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nullable final Editor editor,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        final PsiElement myPsiElement = startElement;

        if (editor == null || !FileModificationService.getInstance().prepareFileForWrite(myPsiElement.getContainingFile())) {
            return;
        }
        if (myPsiElement instanceof PsiEnumConstant) {
            final boolean hasClassInitializer = ((PsiEnumConstant) myPsiElement).getInitializingClass() != null;
            final MemberChooser<PsiMethodMember> chooser = chooseMethodsToImplement(editor, startElement,
                ((PsiEnumConstant) myPsiElement).getContainingClass(), hasClassInitializer
            );
            if (chooser == null) {
                return;
            }

            final List<PsiMethodMember> selectedElements = chooser.getSelectedElements();
            if (selectedElements == null || selectedElements.isEmpty()) {
                return;
            }

            new WriteCommandAction(project, file) {
                @Override
                protected void run(final Result result) throws Throwable {
                    final PsiClass psiClass = ((PsiEnumConstant) myPsiElement).getOrCreateInitializingClass();
                    OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(
                        editor,
                        psiClass,
                        selectedElements,
                        chooser.isCopyJavadoc(),
                        chooser.isInsertOverrideAnnotation()
                    );
                }
            }.execute();
        }
        else {
            OverrideImplementUtil.chooseAndImplementMethods(project, editor, (PsiClass) myPsiElement);
        }
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }


    @Nullable
    protected static MemberChooser<PsiMethodMember> chooseMethodsToImplement(
        Editor editor,
        PsiElement startElement,
        PsiClass aClass,
        boolean implemented
    ) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT);

        final Collection<CandidateInfo> overrideImplement =
            OverrideImplementExploreUtil.getMapToOverrideImplement(aClass, true, implemented).values();
        return OverrideImplementUtil
            .showOverrideImplementChooser(editor, startElement, true, overrideImplement, ContainerUtil.<CandidateInfo>newArrayList());
    }
}
