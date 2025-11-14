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

/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.java.impl.refactoring.inlineSuperClass;

import com.intellij.java.impl.refactoring.inline.JavaInlineActionHandler;
import com.intellij.java.indexing.search.searches.DirectClassInheritorsSearch;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiReferenceList;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;

import java.util.Collection;

@ExtensionImpl
public class InlineSuperClassRefactoringHandler extends JavaInlineActionHandler {
    public static final LocalizeValue REFACTORING_NAME = JavaRefactoringLocalize.inlineSuperClass();

    @Override
    public boolean isEnabledOnElement(PsiElement element) {
        return element instanceof PsiClass;
    }

    @Override
    @RequiredReadAction
    public boolean canInlineElement(PsiElement element) {
        if (!(element instanceof PsiClass)) {
            return false;
        }
        if (element.getLanguage() != JavaLanguage.INSTANCE) {
            return false;
        }
        Collection<PsiClass> inheritors = DirectClassInheritorsSearch.search((PsiClass) element).findAll();
        return inheritors.size() > 0;
    }

    @Override
    @RequiredUIAccess
    public void inlineElement(Project project, Editor editor, PsiElement element) {
        PsiClass superClass = (PsiClass) element;
        Collection<PsiClass> inheritors = DirectClassInheritorsSearch.search((PsiClass) element).findAll();
        if (!superClass.getManager().isInProject(superClass)) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                JavaRefactoringLocalize.inlineSuperNonProjectClassWarningMessage(),
                REFACTORING_NAME,
                null
            );
            return;
        }

        for (PsiClass inheritor : inheritors) {
            if (PsiTreeUtil.isAncestor(superClass, inheritor, false)) {
                CommonRefactoringUtil.showErrorHint(
                    project,
                    editor,
                    JavaRefactoringLocalize.inlineSuperNoInnerClass(inheritor.getName()),
                    REFACTORING_NAME,
                    null
                );
                return;
            }
            if (inheritor instanceof PsiAnonymousClass) {
                CommonRefactoringUtil.showErrorHint(
                    project,
                    editor,
                    JavaRefactoringLocalize.inlineSuperNoAnonymousClass(),
                    REFACTORING_NAME,
                    null
                );
                return;
            }
        }

        PsiClass chosen = null;
        PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
        if (reference != null) {
            PsiElement resolve = reference.resolve();
            if (resolve == superClass) {
                PsiElement referenceElement = reference.getElement();
                if (referenceElement != null
                    && referenceElement.getParent() instanceof PsiReferenceList refList
                    && refList.getParent() instanceof PsiClass psiClass
                    && inheritors.contains(psiClass)) {
                    chosen = psiClass;
                }
            }
        }
        new InlineSuperClassRefactoringDialog(project, superClass, chosen, inheritors.toArray(new PsiClass[inheritors.size()])).show();
    }
}