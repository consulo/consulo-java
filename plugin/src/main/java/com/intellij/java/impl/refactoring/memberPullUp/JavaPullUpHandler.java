/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.memberPullUp;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfoStorage;
import com.intellij.java.language.psi.*;
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.ElementsHandler;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dsl
 * @since 2002-06-18
 */
public class JavaPullUpHandler implements RefactoringActionHandler, PullUpDialog.Callback, ElementsHandler {
    public static final LocalizeValue REFACTORING_NAME = RefactoringLocalize.pullMembersUpTitle();
    private PsiClass mySubclass;
    private Project myProject;

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        int offset = editor.getCaretModel().getOffset();
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        PsiElement element = file.findElementAt(offset);

        while (true) {
            if (element == null || element instanceof PsiFile) {
                LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
                    RefactoringLocalize.theCaretShouldBePositionedInsideAClassToPullMembersFrom()
                );
                CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.MEMBERS_PULL_UP);
                return;
            }

            if (!CommonRefactoringUtil.checkReadOnlyStatus(project, element)) {
                return;
            }

            if (element instanceof PsiClass || element instanceof PsiField || element instanceof PsiMethod) {
                invoke(project, new PsiElement[]{element}, dataContext);
                return;
            }
            element = element.getParent();
        }
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        if (elements.length != 1) {
            return;
        }
        myProject = project;

        PsiElement element = elements[0];
        PsiClass aClass;
        PsiElement aMember = null;

        if (element instanceof PsiClass psiClass) {
            aClass = psiClass;
        }
        else if (element instanceof PsiMethod method) {
            aClass = method.getContainingClass();
            aMember = element;
        }
        else if (element instanceof PsiField field) {
            aClass = field.getContainingClass();
            aMember = element;
        }
        else {
            return;
        }

        invoke(project, dataContext, aClass, aMember);
    }

    @RequiredUIAccess
    private void invoke(Project project, DataContext dataContext, PsiClass aClass, PsiElement aMember) {
        Editor editor = dataContext != null ? dataContext.getData(Editor.KEY) : null;
        if (aClass == null) {
            LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
                RefactoringLocalize.isNotSupportedInTheCurrentContext(REFACTORING_NAME)
            );
            CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.MEMBERS_PULL_UP);
            return;
        }

        List<PsiClass> bases = RefactoringHierarchyUtil.createBasesList(aClass, false, true);

        if (bases.isEmpty()) {
            PsiClass containingClass = aClass.getContainingClass();
            if (containingClass != null) {
                invoke(project, dataContext, containingClass, aClass);
                return;
            }
            LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
                RefactoringLocalize.classDoesNotHaveBaseClassesInterfacesInCurrentProject(aClass.getQualifiedName())
            );
            CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.MEMBERS_PULL_UP);
            return;
        }


        mySubclass = aClass;
        MemberInfoStorage memberInfoStorage = new MemberInfoStorage(mySubclass, element -> true);
        List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(mySubclass);
        PsiManager manager = mySubclass.getManager();

        for (MemberInfoBase<PsiMember> member : members) {
            if (manager.areElementsEquivalent(member.getMember(), aMember)) {
                member.setChecked(true);
                break;
            }
        }

        PullUpDialog dialog = new PullUpDialog(project, aClass, bases, memberInfoStorage, this);

        dialog.show();
    }

    @Override
    @RequiredUIAccess
    public boolean checkConflicts(PullUpDialog dialog) {
        List<MemberInfo> infos = dialog.getSelectedMemberInfos();
        MemberInfo[] memberInfos = infos.toArray(new MemberInfo[infos.size()]);
        PsiClass superClass = dialog.getSuperClass();
        if (!checkWritable(superClass, memberInfos)) {
            return false;
        }
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> Application.get().runReadAction(() -> {
                PsiDirectory targetDirectory = superClass.getContainingFile().getContainingDirectory();
                PsiJavaPackage targetPackage =
                    targetDirectory != null ? JavaDirectoryService.getInstance().getPackage(targetDirectory) : null;
                conflicts.putAllValues(PullUpConflictsUtil.checkConflicts(
                    memberInfos,
                    mySubclass,
                    superClass,
                    targetPackage,
                    targetDirectory,
                    dialog.getContainmentVerifier()
                ));
            }),
            RefactoringLocalize.detectingPossibleConflicts(),
            true,
            myProject
        )) {
            return false;
        }
        if (!conflicts.isEmpty()) {
            ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
            conflictsDialog.show();
            boolean ok = conflictsDialog.isOK();
            if (!ok && conflictsDialog.isShowConflicts()) {
                dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
            }
            return ok;
        }
        return true;
    }

    @RequiredUIAccess
    private boolean checkWritable(PsiClass superClass, MemberInfo[] infos) {
        if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, superClass)) {
            return false;
        }
        for (MemberInfo info : infos) {
            if (info.getMember() instanceof PsiClass && info.getOverrides() != null) {
                continue;
            }
            if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, info.getMember())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isEnabledOnElements(PsiElement[] elements) {
        /*
        if (elements.length == 1) {
            return elements[0] instanceof PsiClass || elements[0] instanceof PsiField || elements[0] instanceof PsiMethod;
        }
        else if (elements.length > 1){
            for (int  idx = 0;  idx < elements.length;  idx++) {
                PsiElement element = elements[idx];
                if (!(element instanceof PsiField || element instanceof PsiMethod)) return false;
            }
            return true;
        }
        return false;
        */
        // todo: multiple selection etc
        return elements.length == 1 && elements[0] instanceof PsiClass;
    }
}
