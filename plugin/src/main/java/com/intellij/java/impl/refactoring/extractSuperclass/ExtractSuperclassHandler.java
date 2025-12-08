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
package com.intellij.java.impl.refactoring.extractSuperclass;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.extractInterface.ExtractClassUtil;
import com.intellij.java.impl.refactoring.memberPullUp.PullUpConflictsUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.progress.ProgressManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.ElementsHandler;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.ui.util.DocCommentPolicy;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.*;
import consulo.language.util.IncorrectOperationException;
import consulo.localHistory.LocalHistory;
import consulo.localHistory.LocalHistoryAction;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.undoRedo.CommandProcessor;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.List;

/**
 * @since 2001-10-25
 * @author Jeka
 */
public class ExtractSuperclassHandler implements RefactoringActionHandler, ExtractSuperclassDialog.Callback, ElementsHandler {
    private static final Logger LOG = Logger.getInstance(ExtractSuperclassHandler.class);

    public static final LocalizeValue REFACTORING_NAME = RefactoringLocalize.extractSuperclassTitle();

    private PsiClass mySubclass;
    private Project myProject;

    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        while (true) {
            if (element == null || element instanceof PsiFile) {
                LocalizeValue message =
                    RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.errorWrongCaretPositionClass());
                CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_SUPERCLASS);
                return;
            }
            if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)) {
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
        mySubclass = (PsiClass) elements[0];

        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, mySubclass)) {
            return;
        }

        Editor editor = dataContext != null ? dataContext.getData(Editor.KEY) : null;
        if (mySubclass.isInterface()) {
            LocalizeValue message =
                RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.superclassCannotBeExtractedFromAnInterface());
            CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_SUPERCLASS);
            return;
        }

        if (mySubclass.isEnum()) {
            LocalizeValue message =
                RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.superclassCannotBeExtractedFromAnEnum());
            CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.EXTRACT_SUPERCLASS);
            return;
        }

        List<MemberInfo> memberInfos = MemberInfo.extractClassMembers(mySubclass, element -> true, false);

        ExtractSuperclassDialog dialog =
            new ExtractSuperclassDialog(project, mySubclass, memberInfos, ExtractSuperclassHandler.this);
        dialog.show();
        if (!dialog.isOK() || !dialog.isExtractSuperclass()) {
            return;
        }

        CommandProcessor.getInstance().newCommand()
            .project(myProject)
            .name(REFACTORING_NAME)
            .inWriteAction()
            .run(() -> doRefactoring(project, mySubclass, dialog));
    }

    @Override
    @RequiredUIAccess
    public boolean checkConflicts(ExtractSuperclassDialog dialog) {
        MemberInfo[] infos = ArrayUtil.toObjectArray(dialog.getSelectedMemberInfos(), MemberInfo.class);
        PsiDirectory targetDirectory = dialog.getTargetDirectory();
        PsiJavaPackage targetPackage;
        if (targetDirectory != null) {
            targetPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory);
        }
        else {
            targetPackage = null;
        }
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> {
                PsiClass superClass = mySubclass.getExtendsListTypes().length > 0 ? mySubclass.getSuperClass() : null;
                conflicts.putAllValues(PullUpConflictsUtil.checkConflicts(
                    infos,
                    mySubclass,
                    superClass,
                    targetPackage,
                    targetDirectory,
                    dialog.getContainmentVerifier(),
                    false
                ));
            },
            RefactoringLocalize.detectingPossibleConflicts(),
            true,
            myProject
        )) {
            return false;
        }
        ExtractSuperClassUtil.checkSuperAccessible(targetDirectory, conflicts, mySubclass);
        return ExtractSuperClassUtil.showConflicts(dialog, conflicts, myProject);
    }

    // invoked inside Command and Atomic action
    @RequiredWriteAction
    private void doRefactoring(Project project, PsiClass subclass, ExtractSuperclassDialog dialog) {
        String superclassName = dialog.getExtractedSuperName();
        PsiDirectory targetDirectory = dialog.getTargetDirectory();
        MemberInfo[] selectedMemberInfos = ArrayUtil.toObjectArray(dialog.getSelectedMemberInfos(), MemberInfo.class);
        DocCommentPolicy javaDocPolicy = new DocCommentPolicy(dialog.getDocCommentPolicy());
        LocalHistoryAction a = LocalHistory.getInstance().startAction(getCommandName(subclass, superclassName));
        try {
            PsiClass superclass;

            try {
                superclass = ExtractSuperClassUtil.extractSuperClass(
                    project,
                    targetDirectory,
                    superclassName,
                    subclass,
                    selectedMemberInfos,
                    javaDocPolicy
                );
            }
            finally {
                a.finish();
            }

            // ask whether to search references to subclass and turn them into refs to superclass if possible
            if (superclass != null) {
                SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
                SmartPsiElementPointer<PsiClass> classPointer = pointerManager.createSmartPsiElementPointer(subclass);
                SmartPsiElementPointer<PsiClass> interfacePointer = pointerManager.createSmartPsiElementPointer(superclass);
                SwingUtilities.invokeLater(() -> ExtractClassUtil.askAndTurnRefsToSuper(project, classPointer, interfacePointer));
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    @RequiredReadAction
    private String getCommandName(PsiClass subclass, String newName) {
        return RefactoringLocalize.extractSuperclassCommandName(newName, DescriptiveNameUtil.getDescriptiveName(subclass)).get();
    }

    @Override
    public boolean isEnabledOnElements(PsiElement[] elements) {
        return elements.length == 1 && elements[0] instanceof PsiClass psiClass && !psiClass.isInterface() && !psiClass.isEnum();
    }
}
