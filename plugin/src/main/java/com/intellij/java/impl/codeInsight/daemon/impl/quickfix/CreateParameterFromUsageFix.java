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

import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.java.impl.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.java.impl.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.java.impl.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.application.Result;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.Comparing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Mike
 */
public class CreateParameterFromUsageFix extends CreateVarFromUsageFix {
    private static final Logger LOG = Logger.getInstance(CreateParameterFromUsageFix.class);

    public CreateParameterFromUsageFix(PsiReferenceExpression referenceElement) {
        super(referenceElement);
        setText(JavaQuickFixLocalize.createParameterFromUsageFamily());
    }

    @Override
    protected boolean isAvailableImpl(int offset) {
        if (!super.isAvailableImpl(offset)) {
            return false;
        }
        if (myReferenceExpression.isQualified()) {
            return false;
        }
        PsiElement scope = myReferenceExpression;
        do {
            scope = PsiTreeUtil.getParentOfType(scope, PsiMethod.class, PsiClass.class);
            if (!(scope instanceof PsiAnonymousClass)) {
                return scope instanceof PsiMethod method
                    && method.getParameterList().isPhysical();
            }
        }
        while (true);
    }

    @Override
    public LocalizeValue getText(String varName) {
        return JavaQuickFixLocalize.createParameterFromUsageText(varName);
    }

    @Override
    @RequiredUIAccess
    protected void invokeImpl(PsiClass targetClass) {
        if (CreateFromUsageUtils.isValidReference(myReferenceExpression, false)) {
            return;
        }

        final Project project = myReferenceExpression.getProject();

        PsiType[] expectedTypes = CreateFromUsageUtils.guessType(myReferenceExpression, false);
        PsiType type = expectedTypes[0];

        String varName = myReferenceExpression.getReferenceName();
        PsiMethod method = PsiTreeUtil.getParentOfType(myReferenceExpression, PsiMethod.class);
        LOG.assertTrue(method != null);
        method = IntroduceParameterHandler.chooseEnclosingMethod(method);
        if (method == null) {
            return;
        }

        method = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringLocalize.toRefactor());
        if (method == null) {
            return;
        }

        List<ParameterInfoImpl> parameterInfos = new ArrayList<>(Arrays.asList(ParameterInfoImpl.fromMethod(method)));
        ParameterInfoImpl parameterInfo = new ParameterInfoImpl(-1, varName, type, PsiTypesUtil.getDefaultValueOfType(type), false);
        if (!method.isVarArgs()) {
            parameterInfos.add(parameterInfo);
        }
        else {
            parameterInfos.add(parameterInfos.size() - 1, parameterInfo);
        }

        Application application = Application.get();
        if (application.isUnitTestMode()) {
            ParameterInfoImpl[] array = parameterInfos.toArray(new ParameterInfoImpl[parameterInfos.size()]);
            String modifier = PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(method.getModifierList()));
            ChangeSignatureProcessor processor =
                new ChangeSignatureProcessor(project, method, false, modifier, method.getName(), method.getReturnType(), array);
            processor.run();
        }
        else {
            PsiMethod finalMethod = method;
            application.invokeLater(() -> {
                if (project.isDisposed()) {
                    return;
                }
                try {
                    JavaChangeSignatureDialog dialog = JavaChangeSignatureDialog.createAndPreselectNew(
                        project,
                        finalMethod,
                        parameterInfos,
                        true,
                        myReferenceExpression
                    );
                    dialog.setParameterInfos(parameterInfos);
                    dialog.show();
                    if (dialog.isOK()) {
                        for (ParameterInfoImpl info : parameterInfos) {
                            if (info.getOldIndex() == -1) {
                                String newParamName = info.getName();
                                if (!Comparing.strEqual(varName, newParamName)) {
                                    final PsiExpression newExpr =
                                        JavaPsiFacade.getElementFactory(project).createExpressionFromText(newParamName, finalMethod);
                                    new WriteCommandAction(project) {
                                        @Override
                                        @RequiredWriteAction
                                        protected void run(Result result) throws Throwable {
                                            PsiReferenceExpression[] refs = CreateFromUsageUtils.collectExpressions(
                                                myReferenceExpression,
                                                PsiMember.class,
                                                PsiFile.class
                                            );
                                            for (PsiReferenceExpression ref : refs) {
                                                ref.replace(newExpr.copy());
                                            }
                                        }
                                    }.execute();
                                }
                                break;
                            }
                        }
                    }
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Override
    protected boolean isAllowOuterTargetClass() {
        return false;
    }
}
