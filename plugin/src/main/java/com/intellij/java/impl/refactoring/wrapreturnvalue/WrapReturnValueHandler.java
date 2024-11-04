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
package com.intellij.java.impl.refactoring.wrapreturnvalue;

import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.RefactorJBundle;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.codeEditor.ScrollingModel;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;

class WrapReturnValueHandler implements RefactoringActionHandler {
    public static final String REFACTORING_NAME = RefactorJBundle.message("wrap.return.value");

    @RequiredUIAccess
    public void invoke(
        @Nonnull Project project,
        Editor editor,
        PsiFile file,
        DataContext dataContext
    ) {
        final ScrollingModel scrollingModel = editor.getScrollingModel();
        scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
        final PsiElement element = dataContext.getData(PsiElement.KEY);
        PsiMethod selectedMethod = null;
        if (element instanceof PsiMethod method) {
            selectedMethod = method;
        }
        else {
            final CaretModel caretModel = editor.getCaretModel();
            final int position = caretModel.getOffset();
            PsiElement selectedElement = file.findElementAt(position);
            while (selectedElement != null) {
                if (selectedElement instanceof PsiMethod method) {
                    selectedMethod = method;
                    break;
                }
                selectedElement = selectedElement.getParent();
            }
        }
        if (selectedMethod == null) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message(
                    "the.caret.should.be.positioned.at.the.name.of.the.method.to.be.refactored"),
                null,
                this.getHelpID()
            );
            return;
        }
        invoke(project, selectedMethod, editor);
    }

    protected String getRefactoringName() {
        return REFACTORING_NAME;
    }

    protected String getHelpID() {
        return HelpID.WrapReturnValue;
    }

    @RequiredUIAccess
    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        if (elements.length != 1) {
            return;
        }
        PsiMethod method = PsiTreeUtil.getParentOfType(elements[0], PsiMethod.class, false);
        if (method == null) {
            return;
        }
        Editor editor = dataContext.getData(Editor.KEY);
        invoke(project, method, editor);
    }

    @RequiredUIAccess
    private void invoke(final Project project, PsiMethod method, Editor editor) {
        if (method.isConstructor()) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message("constructor.returns.can.not.be.wrapped"),
                null,
                this.getHelpID()
            );
            return;
        }
        final PsiType returnType = method.getReturnType();
        if (PsiType.VOID.equals(returnType)) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message("method.selected.returns.void"),
                null,
                this.getHelpID()
            );
            return;
        }
        method = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringLocalize.toRefactor().get());
        if (method == null) {
            return;
        }

        if (method instanceof PsiCompiledElement) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message(
                    "the.selected.method.cannot.be.wrapped.because.it.is.defined.in.a.non.project.class"),
                null,
                this.getHelpID()
            );
            return;
        }

        new WrapReturnValueDialog(method).show();
    }
}
