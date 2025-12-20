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

import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RemoveUnusedParameterFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final String myName;

    public RemoveUnusedParameterFix(PsiParameter parameter) {
        super(parameter);
        myName = parameter.getName();
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return JavaQuickFixLocalize.removeUnusedParameterText(myName);
    }

    @Override
    public boolean isAvailable(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        PsiParameter myParameter = (PsiParameter) startElement;
        return myParameter.isValid()
            && myParameter.getDeclarationScope() instanceof PsiMethod
            && myParameter.getManager().isInProject(myParameter);
    }

    @Override
    public void invoke(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nullable Editor editor,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        PsiParameter myParameter = (PsiParameter) startElement;
        if (!FileModificationService.getInstance().prepareFileForWrite(myParameter.getContainingFile())) {
            return;
        }
        removeReferences(myParameter);
    }

    private static void removeReferences(PsiParameter parameter) {
        PsiMethod method = (PsiMethod) parameter.getDeclarationScope();
        ChangeSignatureProcessor processor = new ChangeSignatureProcessor(parameter.getProject(),
            method,
            false, null,
            method.getName(),
            method.getReturnType(),
            getNewParametersInfo(method, parameter)
        );
        processor.run();
    }

    public static ParameterInfoImpl[] getNewParametersInfo(PsiMethod method, PsiParameter parameterToRemove) {
        List<ParameterInfoImpl> result = new ArrayList<ParameterInfoImpl>();
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            if (!Comparing.equal(parameter, parameterToRemove)) {
                result.add(new ParameterInfoImpl(i, parameter.getName(), parameter.getType()));
            }
        }
        return result.toArray(new ParameterInfoImpl[result.size()]);
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
