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
 * Date: 28-Dec-2009
 */
package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import consulo.annotation.access.RequiredReadAction;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.move.FileReferenceContextUtil;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.move.MoveFileHandler;
import consulo.language.editor.refactoring.move.fileOrDirectory.MoveFilesOrDirectoriesUtil;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.editor.refactoring.ui.RefactoringUIUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.util.IncorrectOperationException;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.usage.NonCodeUsageInfo;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

public class MoveDirectoryWithClassesProcessor extends BaseRefactoringProcessor {
    private final PsiDirectory[] myDirectories;
    private final PsiDirectory myTargetDirectory;
    private final boolean mySearchInComments;
    private final boolean mySearchInNonJavaFiles;
    private final Map<PsiFile, TargetDirectoryWrapper> myFilesToMove;
    private NonCodeUsageInfo[] myNonCodeUsages;
    private final MoveCallback myMoveCallback;

    @RequiredReadAction
    public MoveDirectoryWithClassesProcessor(
        Project project,
        PsiDirectory[] directories,
        PsiDirectory targetDirectory,
        boolean searchInComments,
        boolean searchInNonJavaFiles,
        boolean includeSelf,
        MoveCallback moveCallback
    ) {
        super(project);
        if (targetDirectory != null) {
            List<PsiDirectory> dirs = new ArrayList<>(Arrays.asList(directories));
            for (Iterator<PsiDirectory> iterator = dirs.iterator(); iterator.hasNext(); ) {
                PsiDirectory directory = iterator.next();
                if (targetDirectory.equals(directory.getParentDirectory()) || targetDirectory.equals(directory)) {
                    iterator.remove();
                }
            }
            directories = dirs.toArray(new PsiDirectory[dirs.size()]);
        }
        myDirectories = directories;
        myTargetDirectory = targetDirectory;
        mySearchInComments = searchInComments;
        mySearchInNonJavaFiles = searchInNonJavaFiles;
        myMoveCallback = moveCallback;
        myFilesToMove = new HashMap<>();
        for (PsiDirectory dir : directories) {
            collectFiles2Move(myFilesToMove, dir, includeSelf ? dir.getParentDirectory() : dir, getTargetDirectory(dir));
        }
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        PsiElement[] elements = new PsiElement[myFilesToMove.size()];
        PsiFile[] classes = PsiUtilCore.toPsiFileArray(myFilesToMove.keySet());
        System.arraycopy(classes, 0, elements, 0, classes.length);
        return new MoveMultipleElementsViewDescriptor(elements, getTargetName());
    }

    protected String getTargetName() {
        return RefactoringUIUtil.getDescription(getTargetDirectory(null).getTargetDirectory(), false);
    }

    @Nonnull
    @Override
    public UsageInfo[] findUsages() {
        List<UsageInfo> usages = new ArrayList<>();
        for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
            helper.findUsages(myFilesToMove.keySet(), myDirectories, usages, mySearchInComments, mySearchInNonJavaFiles, myProject);
        }
        return UsageViewUtil.removeDuplicatedUsages(usages.toArray(new UsageInfo[usages.size()]));
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        MultiMap<PsiElement, String> conflicts = new MultiMap<>();
        for (PsiFile psiFile : myFilesToMove.keySet()) {
            try {
                myFilesToMove.get(psiFile).checkMove(psiFile);
            }
            catch (IncorrectOperationException e) {
                conflicts.putValue(psiFile, e.getMessage());
            }
        }
        for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
            helper.preprocessUsages(myProject, myFilesToMove.keySet(), refUsages.get(), myTargetDirectory, conflicts);
        }
        return showConflicts(conflicts, refUsages.get());
    }

    @Override
    protected void refreshElements(@Nonnull PsiElement[] elements) {
    }

    @Override
    @RequiredUIAccess
    public void performRefactoring(@Nonnull UsageInfo[] usages) {
        //try to create all directories beforehand
        try {
            //top level directories should be created even if they are empty
            for (PsiDirectory directory : myDirectories) {
                getResultDirectory(directory).findOrCreateTargetDirectory();
            }
            for (PsiFile psiFile : myFilesToMove.keySet()) {
                myFilesToMove.get(psiFile).findOrCreateTargetDirectory();
            }

            DumbService.getInstance(myProject).completeJustSubmittedTasks();
        }
        catch (IncorrectOperationException e) {
            Messages.showErrorDialog(myProject, e.getMessage(), CommonLocalize.titleError().get());
            return;
        }
        try {
            List<PsiFile> movedFiles = new ArrayList<>();
            Map<PsiElement, PsiElement> oldToNewElementsMapping = new HashMap<>();
            for (PsiFile psiFile : myFilesToMove.keySet()) {
                for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
                    helper.beforeMove(psiFile);
                }
                RefactoringElementListener listener = getTransaction().getElementListener(psiFile);
                PsiDirectory moveDestination = myFilesToMove.get(psiFile).getTargetDirectory();

                for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
                    boolean processed = helper.move(psiFile, moveDestination, oldToNewElementsMapping, movedFiles, listener);
                    if (processed) {
                        break;
                    }
                }
            }
            for (PsiElement newElement : oldToNewElementsMapping.values()) {
                for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
                    helper.afterMove(newElement);
                }
            }

            // fix references in moved files to outer files
            for (PsiFile movedFile : movedFiles) {
                MoveFileHandler.forElement(movedFile).updateMovedFile(movedFile);
                FileReferenceContextUtil.decodeFileReferences(movedFile);
            }

            myNonCodeUsages = CommonMoveUtil.retargetUsages(usages, oldToNewElementsMapping);
            for (MoveDirectoryWithClassesHelper helper : MoveDirectoryWithClassesHelper.findAll()) {
                helper.postProcessUsages(usages, dir -> getResultDirectory(dir).getTargetDirectory());
            }
            for (PsiDirectory directory : myDirectories) {
                directory.delete();
            }
        }
        catch (IncorrectOperationException e) {
            myNonCodeUsages = new NonCodeUsageInfo[0];
            RefactoringUIUtil.processIncorrectOperation(myProject, e);
        }
    }

    @RequiredReadAction
    private TargetDirectoryWrapper getResultDirectory(PsiDirectory dir) {
        return myTargetDirectory != null
            ? new TargetDirectoryWrapper(myTargetDirectory, dir.getName())
            : getTargetDirectory(dir);
    }

    @Override
    protected void performPsiSpoilingRefactoring() {
        if (myNonCodeUsages == null) {
            return; //refactoring was aborted
        }
        RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
        if (myMoveCallback != null) {
            myMoveCallback.refactoringCompleted();
        }
    }

    @RequiredReadAction
    private static void collectFiles2Move(
        Map<PsiFile, TargetDirectoryWrapper> files2Move,
        PsiDirectory directory,
        PsiDirectory rootDirectory,
        @Nonnull TargetDirectoryWrapper targetDirectory
    ) {
        PsiElement[] children = directory.getChildren();
        String relativePath = VfsUtilCore.getRelativePath(directory.getVirtualFile(), rootDirectory.getVirtualFile(), '/');

        TargetDirectoryWrapper newTargetDirectory = relativePath.isEmpty()
            ? targetDirectory
            : targetDirectory.findOrCreateChild(relativePath);
        for (PsiElement child : children) {
            if (child instanceof PsiFile file) {
                files2Move.put(file, newTargetDirectory);
            }
            else if (child instanceof PsiDirectory psiDirectory) {
                collectFiles2Move(files2Move, psiDirectory, directory, newTargetDirectory);
            }
        }
    }

    @Nonnull
    @Override
    protected String getCommandName() {
        return RefactoringLocalize.movingDirectoriesCommand().get();
    }

    public TargetDirectoryWrapper getTargetDirectory(PsiDirectory dir) {
        return new TargetDirectoryWrapper(myTargetDirectory);
    }

    public static class TargetDirectoryWrapper {
        private TargetDirectoryWrapper myParentDirectory;
        private PsiDirectory myTargetDirectory;
        private String myRelativePath;

        public TargetDirectoryWrapper(PsiDirectory targetDirectory) {
            myTargetDirectory = targetDirectory;
        }

        public TargetDirectoryWrapper(TargetDirectoryWrapper parentDirectory, String relativePath) {
            myParentDirectory = parentDirectory;
            myRelativePath = relativePath;
        }

        public TargetDirectoryWrapper(PsiDirectory parentDirectory, String relativePath) {
            myTargetDirectory = parentDirectory.findSubdirectory(relativePath);
            //in case it was null
            myParentDirectory = new TargetDirectoryWrapper(parentDirectory);
            myRelativePath = relativePath;
        }

        public PsiDirectory findOrCreateTargetDirectory() throws IncorrectOperationException {
            if (myTargetDirectory == null) {
                PsiDirectory root = myParentDirectory.findOrCreateTargetDirectory();

                myTargetDirectory = root.findSubdirectory(myRelativePath);
                if (myTargetDirectory == null) {
                    myTargetDirectory = root.createSubdirectory(myRelativePath);
                }
            }
            return myTargetDirectory;
        }

        @Nullable
        public PsiDirectory getTargetDirectory() {
            return myTargetDirectory;
        }

        public TargetDirectoryWrapper findOrCreateChild(String relativePath) {
            if (myTargetDirectory != null) {
                PsiDirectory psiDirectory = myTargetDirectory.findSubdirectory(relativePath);
                if (psiDirectory != null) {
                    return new TargetDirectoryWrapper(psiDirectory);
                }
            }
            return new TargetDirectoryWrapper(this, relativePath);
        }

        public void checkMove(PsiFile psiFile) throws IncorrectOperationException {
            if (myTargetDirectory != null) {
                MoveFilesOrDirectoriesUtil.checkMove(psiFile, myTargetDirectory);
            }
        }
    }
}
