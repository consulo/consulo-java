/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.util.LanguageUndoUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageViewUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class VariableTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    static final Logger LOG = Logger.getInstance(VariableTypeFix.class);

    private final PsiType myReturnType;
    protected final String myName;

    public VariableTypeFix(@Nonnull PsiVariable variable, PsiType toReturn) {
        super(variable);
        myReturnType = GenericsUtil.getVariableTypeByExpressionType(toReturn);
        myName = variable.getName();
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return JavaQuickFixLocalize.fixVariableTypeText(
            UsageViewUtil.getType(getStartElement()),
            myName,
            getReturnType().getCanonicalText()
        );
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public boolean isAvailable(
        @Nonnull Project project,
        @Nonnull PsiFile file,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        PsiVariable myVariable = (PsiVariable) startElement;
        return myVariable.isValid()
            && myVariable.getTypeElement() != null
            && myVariable.getManager().isInProject(myVariable)
            && getReturnType() != null
            && getReturnType().isValid()
            && !TypeConversionUtil.isNullType(getReturnType())
            && !TypeConversionUtil.isVoidType(getReturnType());
    }

    @Override
    public void invoke(
        @Nonnull final Project project,
        @Nonnull final PsiFile file,
        @Nullable Editor editor,
        @Nonnull PsiElement startElement,
        @Nonnull PsiElement endElement
    ) {
        final PsiVariable myVariable = (PsiVariable) startElement;
        if (changeMethodSignatureIfNeeded(myVariable)) {
            return;
        }
        if (!FileModificationService.getInstance().prepareFileForWrite(myVariable.getContainingFile())) {
            return;
        }
        new WriteCommandAction.Simple(project, getText().get(), file) {
            @Override
            protected void run() throws Throwable {
                try {
                    myVariable.normalizeDeclaration();
                    PsiTypeElement typeElement = myVariable.getTypeElement();
                    LOG.assertTrue(typeElement != null, myVariable.getClass());
                    PsiTypeElement newTypeElement =
                        JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createTypeElement(getReturnType());
                    typeElement.replace(newTypeElement);
                    JavaCodeStyleManager.getInstance(project).shortenClassReferences(myVariable);
                    LanguageUndoUtil.markPsiFileForUndo(file);
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            }
        }.execute();
    }

    @RequiredUIAccess
    private boolean changeMethodSignatureIfNeeded(PsiVariable myVariable) {
        if (myVariable instanceof PsiParameter param && param.getDeclarationScope() instanceof PsiMethod method) {
            PsiMethod psiMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringLocalize.toRefactor());
            if (psiMethod == null) {
                return true;
            }
            int parameterIndex = method.getParameterList().getParameterIndex(param);
            if (!FileModificationService.getInstance().prepareFileForWrite(psiMethod.getContainingFile())) {
                return true;
            }
            List<ParameterInfoImpl> infos = new ArrayList<>();
            int i = 0;
            for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
                boolean changeType = i == parameterIndex;
                infos.add(new ParameterInfoImpl(i++, parameter.getName(), changeType ? getReturnType() : parameter.getType()));
            }

            if (!param.getApplication().isUnitTestMode()) {
                JavaChangeSignatureDialog dialog = new JavaChangeSignatureDialog(psiMethod.getProject(), psiMethod, false, param);
                dialog.setParameterInfos(infos);
                dialog.show();
            }
            else {
                ChangeSignatureProcessor processor = new ChangeSignatureProcessor(
                    psiMethod.getProject(),
                    psiMethod,
                    false,
                    null,
                    psiMethod.getName(),
                    psiMethod.getReturnType(),
                    infos.toArray(new ParameterInfoImpl[infos.size()])
                );
                processor.run();
            }
            return true;
        }
        return false;
    }

    protected PsiType getReturnType() {
        return myReturnType;
    }
}