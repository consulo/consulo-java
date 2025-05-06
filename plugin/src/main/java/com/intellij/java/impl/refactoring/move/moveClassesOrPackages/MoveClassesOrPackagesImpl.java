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

/**
 * created at Nov 27, 2001
 *
 * @author Jeka
 */
package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.util.RefactoringConflictsUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.application.progress.ProgressManager;
import consulo.ide.impl.idea.ide.util.DirectoryChooser;
import consulo.ide.impl.idea.refactoring.rename.DirectoryAsPackageRenameHandlerBase;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.refactoring.util.TextOccurrencesUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localHistory.LocalHistory;
import consulo.localHistory.LocalHistoryAction;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.undoRedo.CommandProcessor;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Comparing;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MoveClassesOrPackagesImpl {
    private static final Logger LOG = Logger.getInstance(MoveClassesOrPackagesImpl.class);

    @RequiredUIAccess
    public static void doMove(
        Project project,
        PsiElement[] adjustedElements,
        PsiElement initialTargetElement,
        MoveCallback moveCallback
    ) {
        if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(adjustedElements), true)) {
            return;
        }

        String initialTargetPackageName = getInitialTargetPackageName(initialTargetElement, adjustedElements);
        PsiDirectory initialTargetDirectory = getInitialTargetDirectory(initialTargetElement, adjustedElements);
        boolean isTargetDirectoryFixed = initialTargetDirectory == null;

        boolean searchTextOccurences = false;
        for (int i = 0; i < adjustedElements.length && !searchTextOccurences; i++) {
            PsiElement psiElement = adjustedElements[i];
            searchTextOccurences = TextOccurrencesUtil.isSearchTextOccurencesEnabled(psiElement);
        }
        MoveClassesOrPackagesDialog moveDialog =
            new MoveClassesOrPackagesDialog(project, searchTextOccurences, adjustedElements, initialTargetElement, moveCallback);
        boolean searchInComments = JavaRefactoringSettings.getInstance().MOVE_SEARCH_IN_COMMENTS;
        boolean searchForTextOccurences = JavaRefactoringSettings.getInstance().MOVE_SEARCH_FOR_TEXT;
        moveDialog.setData(
            adjustedElements,
            initialTargetPackageName,
            initialTargetDirectory,
            isTargetDirectoryFixed,
            initialTargetElement == null,
            searchInComments,
            searchForTextOccurences,
            HelpID.getMoveHelpID(adjustedElements[0])
        );
        moveDialog.show();
    }

    @Nullable
    @RequiredUIAccess
    public static PsiElement[] adjustForMove(Project project, PsiElement[] elements, PsiElement targetElement) {
        PsiElement[] psiElements = new PsiElement[elements.length];
        List<String> names = new ArrayList<>();
        for (int idx = 0; idx < elements.length; idx++) {
            PsiElement element = elements[idx];
            if (element instanceof PsiDirectory directory) {
                PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
                LOG.assertTrue(aPackage != null);
                if (aPackage.getQualifiedName().isEmpty()) { //is default package
                    String message = RefactoringLocalize.movePackageRefactoringCannotBeAppliedToDefaultPackage().get();
                    CommonRefactoringUtil.showErrorMessage(
                        RefactoringLocalize.moveTitle().get(),
                        message,
                        HelpID.getMoveHelpID(element),
                        project
                    );
                    return null;
                }
                if (!checkNesting(project, aPackage, targetElement, true)) {
                    return null;
                }
                if (!isAlreadyChecked(psiElements, idx, aPackage) && !checkMovePackage(project, aPackage)) {
                    return null;
                }
                element = aPackage;
            }
            else if (element instanceof PsiJavaPackage psiPackage) {
                if (!checkNesting(project, psiPackage, targetElement, true)
                    || !checkMovePackage(project, psiPackage)) {
                    return null;
                }
            }
            else if (element instanceof PsiClass aClass) {
                if (aClass instanceof PsiAnonymousClass) {
                    String message = RefactoringLocalize.moveClassRefactoringCannotBeAppliedToAnonymousClasses().get();
                    CommonRefactoringUtil.showErrorMessage(
                        RefactoringLocalize.moveTitle().get(),
                        message,
                        HelpID.getMoveHelpID(element),
                        project
                    );
                    return null;
                }
                if (isClassInnerOrLocal(aClass)) {
                    CommonRefactoringUtil.showErrorMessage(
                        RefactoringLocalize.moveTitle().get(),
                        RefactoringLocalize.cannotPerformRefactoringWithReason(
                            RefactoringLocalize.movingLocalClassesIsNotSupported()
                        ).get(),
                        HelpID.getMoveHelpID(element),
                        project
                    );
                    return null;
                }

                String name = null;
                for (MoveClassHandler nameProvider : MoveClassHandler.EP_NAME.getExtensionList()) {
                    name = nameProvider.getName(aClass);
                    if (name != null) {
                        break;
                    }
                }
                if (name == null) {
                    name = aClass.getContainingFile().getName();
                }

                if (names.contains(name)) {
                    String message = RefactoringBundle
                        .getCannotRefactorMessage(RefactoringLocalize.thereAreGoingToBeMultipleDestinationFilesWithTheSameName().get());
                    CommonRefactoringUtil.showErrorMessage(
                        RefactoringLocalize.moveTitle().get(),
                        message,
                        HelpID.getMoveHelpID(element),
                        project
                    );
                    return null;
                }

                names.add(name);
            }
            psiElements[idx] = element;
        }

        return psiElements;
    }

    static boolean isClassInnerOrLocal(PsiClass aClass) {
        return aClass.getContainingClass() != null || aClass.getQualifiedName() == null;
    }

    private static boolean isAlreadyChecked(PsiElement[] psiElements, int idx, PsiJavaPackage aPackage) {
        for (int i = 0; i < idx; i++) {
            if (Comparing.equal(psiElements[i], aPackage)) {
                return true;
            }
        }
        return false;
    }

    @RequiredUIAccess
    private static boolean checkMovePackage(Project project, PsiJavaPackage aPackage) {
        PsiDirectory[] directories = aPackage.getDirectories();
        VirtualFile[] virtualFiles = aPackage.occursInPackagePrefixes();
        if (directories.length > 1 || virtualFiles.length > 0) {
            StringBuffer message = new StringBuffer();
            RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, aPackage.getQualifiedName());
            if (directories.length > 1) {
                DirectoryAsPackageRenameHandlerBase.buildMultipleDirectoriesInPackageMessage(
                    message,
                    aPackage.getQualifiedName(),
                    directories
                );
                message.append("\n\n");
                LocalizeValue report =
                    RefactoringLocalize.allTheseDirectoriesWillBeMovedAndAllReferencesTo0WillBeChanged(aPackage.getQualifiedName());
                message.append(report.get());
            }
            message.append("\n");
            message.append(RefactoringLocalize.doYouWishToContinue());
            int ret =
                Messages.showYesNoDialog(project, message.toString(), RefactoringLocalize.warningTitle().get(), UIUtil.getWarningIcon());
            if (ret != 0) {
                return false;
            }
        }
        return true;
    }

    @RequiredUIAccess
    static boolean checkNesting(Project project, PsiJavaPackage srcPackage, PsiElement targetElement, boolean showError) {
        PsiJavaPackage targetPackage = targetElement instanceof PsiJavaPackage javaPackage
            ? javaPackage
            : targetElement instanceof PsiDirectory directory
            ? JavaDirectoryService.getInstance().getPackage(directory)
            : null;
        for (PsiJavaPackage curPackage = targetPackage; curPackage != null; curPackage = curPackage.getParentPackage()) {
            if (curPackage.equals(srcPackage)) {
                if (showError) {
                    CommonRefactoringUtil.showErrorMessage(
                        RefactoringLocalize.moveTitle().get(),
                        RefactoringLocalize.cannotMovePackageIntoItself().get(),
                        HelpID.getMoveHelpID(srcPackage), project
                    );
                }
                return false;
            }
        }
        return true;
    }

    public static String getInitialTargetPackageName(PsiElement initialTargetElement, PsiElement[] movedElements) {
        String name = getContainerPackageName(initialTargetElement);
        if (name == null) {
            if (movedElements != null) {
                name = getTargetPackageNameForMovedElement(movedElements[0]);
            }
            if (name == null) {
                PsiDirectory commonDirectory = getCommonDirectory(movedElements);
                if (commonDirectory != null && JavaDirectoryService.getInstance().getPackage(commonDirectory) != null) {
                    name = JavaDirectoryService.getInstance().getPackage(commonDirectory).getQualifiedName();
                }
            }
        }
        if (name == null) {
            name = "";
        }
        return name;
    }

    @Nullable
    private static PsiDirectory getCommonDirectory(PsiElement[] movedElements) {
        PsiDirectory commonDirectory = null;

        for (PsiElement movedElement : movedElements) {
            PsiFile containingFile = movedElement.getContainingFile();
            if (containingFile != null) {
                PsiDirectory containingDirectory = containingFile.getContainingDirectory();
                if (containingDirectory != null) {
                    if (commonDirectory == null) {
                        commonDirectory = containingDirectory;
                    }
                    else {
                        if (commonDirectory != containingDirectory) {
                            return null;
                        }
                    }
                }
            }
        }
        if (commonDirectory != null) {
            return commonDirectory;
        }
        else {
            return null;
        }
    }

    private static String getContainerPackageName(PsiElement psiElement) {
        if (psiElement instanceof PsiJavaPackage javaPackage) {
            return javaPackage.getQualifiedName();
        }
        else if (psiElement instanceof PsiDirectory directory) {
            PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
            return aPackage != null ? aPackage.getQualifiedName() : "";
        }
        else if (psiElement != null) {
            PsiJavaPackage aPackage =
                JavaDirectoryService.getInstance().getPackage(psiElement.getContainingFile().getContainingDirectory());
            return aPackage != null ? aPackage.getQualifiedName() : "";
        }
        else {
            return null;
        }
    }

    private static String getTargetPackageNameForMovedElement(PsiElement psiElement) {
        if (psiElement instanceof PsiJavaPackage psiPackage) {
            PsiJavaPackage parentPackage = psiPackage.getParentPackage();
            return parentPackage != null ? parentPackage.getQualifiedName() : "";
        }
        else if (psiElement instanceof PsiDirectory directory) {
            PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
            return aPackage != null ? getTargetPackageNameForMovedElement(aPackage) : "";
        }
        else if (psiElement != null) {
            PsiJavaPackage aPackage =
                JavaDirectoryService.getInstance().getPackage(psiElement.getContainingFile().getContainingDirectory());
            return aPackage != null ? aPackage.getQualifiedName() : "";
        }
        else {
            return null;
        }
    }


    public static PsiDirectory getInitialTargetDirectory(PsiElement initialTargetElement, PsiElement[] movedElements) {
        PsiDirectory initialTargetDirectory = getContainerDirectory(initialTargetElement);
        if (initialTargetDirectory == null) {
            if (movedElements != null) {
                PsiDirectory commonDirectory = getCommonDirectory(movedElements);
                if (commonDirectory != null) {
                    initialTargetDirectory = commonDirectory;
                }
                else {
                    initialTargetDirectory = getContainerDirectory(movedElements[0]);
                }
            }
        }
        return initialTargetDirectory;
    }

    @Nullable
    public static PsiDirectory getContainerDirectory(PsiElement psiElement) {
        if (psiElement instanceof PsiJavaPackage javaPackage) {
            PsiDirectory[] directories = javaPackage.getDirectories();
            return directories.length == 1 ? directories[0] : null; //??
        }
        if (psiElement instanceof PsiDirectory directory) {
            return directory;
        }
        if (psiElement != null) {
            return psiElement.getContainingFile().getContainingDirectory();
        }
        return null;
    }

    @RequiredUIAccess
    public static void doRearrangePackage(Project project, PsiDirectory[] directories) {
        if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(directories), true)) {
            return;
        }

        List<PsiDirectory> sourceRootDirectories = buildRearrangeTargetsList(project, directories);
        DirectoryChooser chooser = new DirectoryChooser(project);
        chooser.setTitle(RefactoringLocalize.selectSourceRootChooserTitle());
        chooser.fillList(sourceRootDirectories.toArray(new PsiDirectory[sourceRootDirectories.size()]), null, project, "");
        chooser.show();
        if (!chooser.isOK()) {
            return;
        }
        PsiDirectory selectedTarget = chooser.getSelectedDirectory();
        if (selectedTarget == null) {
            return;
        }
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        Runnable analyzeConflicts = () -> RefactoringConflictsUtil.analyzeModuleConflicts(
            project,
            Arrays.asList(directories),
            UsageInfo.EMPTY_ARRAY,
            selectedTarget,
            conflicts
        );
        if (!ProgressManager.getInstance()
            .runProcessWithProgressSynchronously(analyzeConflicts, "Analyze Module Conflicts...", true, project)) {
            return;
        }
        if (!conflicts.isEmpty()) {
            if (project.getApplication().isUnitTestMode()) {
                throw new BaseRefactoringProcessor.ConflictsInTestsException(conflicts.values());
            }
            else {
                ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
                conflictsDialog.show();
                if (!conflictsDialog.isOK()) {
                    return;
                }
            }
        }
        SimpleReference<IncorrectOperationException> ex = SimpleReference.create();
        String commandDescription = RefactoringLocalize.movingDirectoriesCommand().get();
        @RequiredUIAccess
        Runnable runnable = () -> project.getApplication().runWriteAction(() -> {
            LocalHistoryAction a = LocalHistory.getInstance().startAction(commandDescription);
            try {
                rearrangeDirectoriesToTarget(directories, selectedTarget);
            }
            catch (IncorrectOperationException e) {
                ex.set(e);
            }
            finally {
                a.finish();
            }
        });
        CommandProcessor.getInstance().executeCommand(project, runnable, commandDescription, null);
        if (!ex.isNull()) {
            RefactoringUIUtil.processIncorrectOperation(project, ex.get());
        }
    }

    @RequiredUIAccess
    private static List<PsiDirectory> buildRearrangeTargetsList(Project project, PsiDirectory[] directories) {
        VirtualFile[] sourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
        List<PsiDirectory> sourceRootDirectories = new ArrayList<>();
        sourceRoots:
        for (VirtualFile sourceRoot : sourceRoots) {
            PsiDirectory sourceRootDirectory = PsiManager.getInstance(project).findDirectory(sourceRoot);
            if (sourceRootDirectory == null) {
                continue;
            }
            PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(sourceRootDirectory);
            if (aPackage == null) {
                continue;
            }
            String packagePrefix = aPackage.getQualifiedName();
            for (PsiDirectory directory : directories) {
                String qualifiedName = JavaDirectoryService.getInstance().getPackage(directory).getQualifiedName();
                if (!qualifiedName.startsWith(packagePrefix)) {
                    continue sourceRoots;
                }
            }
            sourceRootDirectories.add(sourceRootDirectory);
        }
        return sourceRootDirectories;
    }

    private static void rearrangeDirectoriesToTarget(PsiDirectory[] directories, PsiDirectory selectedTarget)
        throws IncorrectOperationException {
        VirtualFile sourceRoot = selectedTarget.getVirtualFile();
        for (PsiDirectory directory : directories) {
            PsiJavaPackage parentPackage = JavaDirectoryService.getInstance().getPackage(directory).getParentPackage();
            PackageWrapper wrapper = new PackageWrapper(parentPackage);
            PsiDirectory moveTarget = RefactoringUtil.createPackageDirectoryInSourceRoot(wrapper, sourceRoot);
            MoveClassesOrPackagesUtil.moveDirectoryRecursively(directory, moveTarget);
        }
    }
}
