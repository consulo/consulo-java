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
package com.intellij.java.impl.refactoring.removemiddleman;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.RefactorJBundle;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.codeEditor.ScrollingModel;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.util.Set;

public class RemoveMiddlemanHandler implements RefactoringActionHandler {
    private static final String REFACTORING_NAME = RefactorJBundle.message("remove.middleman");
    @NonNls
    static final String REMOVE_METHODS = "refactoring.removemiddleman.remove.methods";

    protected static String getRefactoringName() {
        return REFACTORING_NAME;
    }

    protected static String getHelpID() {
        return HelpID.RemoveMiddleman;
    }

    public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        final ScrollingModel scrollingModel = editor.getScrollingModel();
        scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
        final PsiElement element = dataContext.getData(PsiElement.KEY);
        if (element instanceof PsiField field) {
            invoke(field, editor);
        }
        else {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactorJBundle.message("cannot.perform.the.refactoring") +
                    RefactorJBundle.message("the.caret.should.be.positioned.at.the.name.of.the.field.to.be.refactored"),
                null,
                getHelpID()
            );
        }
    }

    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        if (elements.length != 1) {
            return;
        }
        if (elements[0] instanceof PsiField field) {
            Editor editor = dataContext.getData(Editor.KEY);
            invoke(field, editor);
        }
    }

    private static void invoke(final PsiField field, Editor editor) {
        final Project project = field.getProject();
        final Set<PsiMethod> delegating = DelegationUtils.getDelegatingMethodsForField(field);
        if (delegating.isEmpty()) {
            final String message = RefactorJBundle.message("cannot.perform.the.refactoring") +
                RefactorJBundle.message("field.selected.is.not.used.as.a.delegate");
            CommonRefactoringUtil.showErrorHint(project, editor, message, null, getHelpID());
            return;
        }

        MemberInfo[] infos = new MemberInfo[delegating.size()];
        int i = 0;
        for (PsiMethod method : delegating) {
            final MemberInfo memberInfo = new MemberInfo(method);
            memberInfo.setChecked(true);
            memberInfo.setToAbstract(method.findDeepestSuperMethods().length == 0);
            infos[i++] = memberInfo;
        }
        new RemoveMiddlemanDialog(field, infos).show();
    }
}
