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
package com.intellij.java.impl.refactoring.convertToInstanceMethod;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 */
public class ConvertToInstanceMethodHandler implements RefactoringActionHandler {
    private static final Logger LOG = Logger.getInstance(ConvertToInstanceMethodHandler.class);
    static final LocalizeValue REFACTORING_NAME = RefactoringLocalize.convertToInstanceMethodTitle();

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        PsiElement element = dataContext.getData(PsiElement.KEY);
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        if (element == null) {
            element = file.findElementAt(editor.getCaretModel().getOffset());
        }

        if (element == null) {
            return;
        }
        if (element instanceof PsiIdentifier) {
            element = element.getParent();
        }

        if (!(element instanceof PsiMethod)) {
            LocalizeValue message =
                RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.errorWrongCaretPositionMethod());
            CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.CONVERT_TO_INSTANCE_METHOD);
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("MakeMethodStaticHandler invoked");
        }
        invoke(project, new PsiElement[]{element}, dataContext);
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        if (elements.length != 1 || !(elements[0] instanceof PsiMethod)) {
            return;
        }
        PsiMethod method = (PsiMethod) elements[0];
        if (!method.isStatic()) {
            LocalizeValue message = RefactoringLocalize.converttoinstancemethodMethodIsNotStatic(method.getName());
            Editor editor = dataContext.getData(Editor.KEY);
            CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.CONVERT_TO_INSTANCE_METHOD);
            return;
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        List<PsiParameter> suitableParameters = new ArrayList<>();
        boolean classTypesFound = false;
        boolean resolvableClassesFound = false;
        boolean classesInProjectFound = false;
        for (PsiParameter parameter : parameters) {
            PsiType type = parameter.getType();
            if (type instanceof PsiClassType classType) {
                classTypesFound = true;
                PsiClass psiClass = classType.resolve();
                if (psiClass != null && !(psiClass instanceof PsiTypeParameter)) {
                    resolvableClassesFound = true;
                    boolean inProject = method.getManager().isInProject(psiClass);
                    if (inProject) {
                        classesInProjectFound = true;
                        suitableParameters.add(parameter);
                    }
                }
            }
        }
        if (suitableParameters.isEmpty()) {
            LocalizeValue message;
            if (!classTypesFound) {
                message = RefactoringLocalize.converttoinstancemethodNoParametersWithReferenceType();
            }
            else if (!resolvableClassesFound) {
                message = RefactoringLocalize.converttoinstancemethodAllReferenceTypeParametresHaveUnknownTypes();
            }
            else if (!classesInProjectFound) {
                message = RefactoringLocalize.converttoinstancemethodAllReferenceTypeParametersAreNotInProject();
            }
            else {
                LOG.assertTrue(false);
                return;
            }
            Editor editor = dataContext.getData(Editor.KEY);
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactoringLocalize.cannotPerformRefactoringWithReason(message),
                REFACTORING_NAME,
                HelpID.CONVERT_TO_INSTANCE_METHOD
            );
            return;
        }

        new ConvertToInstanceMethodDialog(
            method,
            suitableParameters.toArray(new PsiParameter[suitableParameters.size()])
        ).show();
    }
}
