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

import com.intellij.java.impl.codeInsight.ExpectedTypeInfo;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.function.Function;

public class CreateMethodQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    protected final String mySignature;
    protected final String myBody;

    private CreateMethodQuickFix(final PsiClass targetClass, String signature, String body) {
        super(targetClass);
        mySignature = signature;
        myBody = body;
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        PsiClass myTargetClass = (PsiClass) getStartElement();
        String signature = myTargetClass == null ? ""
            : PsiFormatUtil.formatMethod(
            createMethod(myTargetClass),
            PsiSubstitutor.EMPTY,
            PsiFormatUtilBase.SHOW_NAME |
                PsiFormatUtilBase.SHOW_TYPE |
                PsiFormatUtilBase.SHOW_PARAMETERS |
                PsiFormatUtilBase.SHOW_RAW_TYPE,
            PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_RAW_TYPE,
            2
        );
        return JavaQuickFixLocalize.createMethodFromUsageText(signature);
    }

    @Override
    public void invoke(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nullable Editor editor,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        PsiClass myTargetClass = (PsiClass) startElement;
        if (!FileModificationService.getInstance().preparePsiElementForWrite(myTargetClass.getContainingFile())) {
            return;
        }

        PsiMethod method = createMethod(myTargetClass);
        List<Pair<PsiExpression, PsiType>> arguments = ContainerUtil.map2List(
            method.getParameterList().getParameters(),
            (Function<PsiParameter, Pair<PsiExpression, PsiType>>) psiParameter -> Pair.create(null, psiParameter.getType())
        );

        method = (PsiMethod) JavaCodeStyleManager.getInstance(project).shortenClassReferences(myTargetClass.add(method));
        CreateMethodFromUsageFix.doCreate(myTargetClass, method, arguments, PsiSubstitutor.EMPTY, ExpectedTypeInfo.EMPTY_ARRAY, method);
    }

    private PsiMethod createMethod(@Nonnull PsiClass myTargetClass) {
        Project project = myTargetClass.getProject();
        JVMElementFactory elementFactory = JVMElementFactories.getFactory(myTargetClass.getLanguage(), project);
        if (elementFactory == null) {
            elementFactory = JavaPsiFacade.getElementFactory(project);
        }
        String methodText = mySignature + (myTargetClass.isInterface() ? ";" : "{" + myBody + "}");
        return elementFactory.createMethodFromText(methodText, null);
    }

    @Nullable
    public static CreateMethodQuickFix createFix(@Nonnull PsiClass targetClass, String signature, String body) {
        CreateMethodQuickFix fix = new CreateMethodQuickFix(targetClass, signature, body);
        try {
            fix.createMethod(targetClass);
            return fix;
        }
        catch (IncorrectOperationException e) {
            return null;
        }
    }
}
