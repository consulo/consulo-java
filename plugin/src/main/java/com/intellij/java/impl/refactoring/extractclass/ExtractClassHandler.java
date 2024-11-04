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
package com.intellij.java.impl.refactoring.extractclass;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.dataContext.DataContext;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.codeEditor.ScrollingModel;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.RefactorJBundle;
import consulo.language.editor.refactoring.ElementsHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;

public class ExtractClassHandler implements ElementsHandler {

    protected static String getHelpID() {
        return HelpID.ExtractClass;
    }

    @Override
    public boolean isEnabledOnElements(PsiElement[] elements) {
        return elements.length == 1 && PsiTreeUtil.getParentOfType(elements[0], PsiClass.class, false) != null;
    }

    public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        final ScrollingModel scrollingModel = editor.getScrollingModel();
        scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
        final CaretModel caretModel = editor.getCaretModel();
        final int position = caretModel.getOffset();
        final PsiElement element = file.findElementAt(position);

        final PsiMember selectedMember = PsiTreeUtil.getParentOfType(element, PsiMember.class, true);
        if (selectedMember == null) {
            //todo
            return;
        }

        PsiClass containingClass = selectedMember.getContainingClass();

        if (containingClass == null && selectedMember instanceof PsiClass) {
            containingClass = (PsiClass)selectedMember;
        }

        if (containingClass == null) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message(
                    "the.caret.should.be.positioned.within.a.class.to.be.refactored"),
                null,
                getHelpID()
            );
            return;
        }
        if (containingClass.isInterface()) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message("the.selected.class.is.an.interface"),
                null,
                getHelpID()
            );
            return;
        }
        if (containingClass.isEnum()) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message("the.selected.class.is.an.enumeration"),
                null,
                getHelpID()
            );
            return;
        }
        if (containingClass.isAnnotationType()) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message(
                    "the.selected.class.is.an.annotation.type"),
                null,
                getHelpID()
            );
            return;
        }
        if (classIsInner(containingClass) && !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message(
                    "the.refactoring.is.not.supported.on.non.static.inner.classes"),
                null,
                getHelpID()
            );
            return;
        }
        if (classIsTrivial(containingClass)) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactorJBundle.message("cannot.perform.the.refactoring") + RefactorJBundle.message(
                    "the.selected.class.has.no.members.to.extract"),
                null,
                getHelpID()
            );
            return;
        }
        new ExtractClassDialog(containingClass, selectedMember).show();
    }

    private static boolean classIsInner(PsiClass aClass) {
        return PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true) != null;
    }

    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        if (elements.length != 1) {
            return;
        }
        final PsiClass containingClass = PsiTreeUtil.getParentOfType(elements[0], PsiClass.class, false);

        final PsiMember selectedMember = PsiTreeUtil.getParentOfType(elements[0], PsiMember.class, false);
        if (containingClass == null) {
            return;
        }
        if (classIsTrivial(containingClass)) {
            return;
        }
        new ExtractClassDialog(containingClass, selectedMember).show();
    }

    private static boolean classIsTrivial(PsiClass containingClass) {
        if (containingClass.getFields().length == 0) {
            final PsiMethod[] methods = containingClass.getMethods();
            if (methods.length == 0) {
                return true;
            }
            for (PsiMethod method : methods) {
                if (method.getBody() != null) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
