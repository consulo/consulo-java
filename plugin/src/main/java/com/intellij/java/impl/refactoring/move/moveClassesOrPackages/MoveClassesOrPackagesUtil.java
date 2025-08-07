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
package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.ide.util.DirectoryChooserUtil;
import consulo.language.editor.refactoring.move.fileOrDirectory.MoveFilesOrDirectoriesUtil;
import consulo.language.editor.refactoring.util.TextOccurrencesUtil;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.UsageInfo;
import consulo.usage.localize.UsageLocalize;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.*;
import java.util.function.Supplier;

public class MoveClassesOrPackagesUtil {
    private static final Logger LOG = Logger.getInstance(MoveClassesOrPackagesUtil.class);

    private MoveClassesOrPackagesUtil() {
    }

    @RequiredReadAction
    public static UsageInfo[] findUsages(
        PsiElement element,
        boolean searchInStringsAndComments,
        boolean searchInNonJavaFiles,
        String newQName
    ) {
        PsiManager manager = element.getManager();

        ArrayList<UsageInfo> results = new ArrayList<>();
        Set<PsiReference> foundReferences = new HashSet<>();

        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
        for (PsiReference reference : ReferencesSearch.search(element, projectScope, false)) {
            TextRange range = reference.getRangeInElement();
            if (foundReferences.contains(reference)) {
                continue;
            }
            results.add(new MoveRenameUsageInfo(
                reference.getElement(),
                reference,
                range.getStartOffset(),
                range.getEndOffset(),
                element,
                false
            ));
            foundReferences.add(reference);
        }

        findNonCodeUsages(searchInStringsAndComments, searchInNonJavaFiles, element, newQName, results);
        element.getApplication().getExtensionPoint(MoveClassHandler.class).forEach(handler -> handler.preprocessUsages(results));
        return results.toArray(new UsageInfo[results.size()]);
    }

    public static void findNonCodeUsages(
        boolean searchInStringsAndComments,
        boolean searchInNonJavaFiles,
        PsiElement element,
        String newQName,
        List<UsageInfo> results
    ) {
        String stringToSearch = getStringToSearch(element);
        if (stringToSearch == null) {
            return;
        }
        TextOccurrencesUtil.findNonCodeUsages(element, stringToSearch, searchInStringsAndComments, searchInNonJavaFiles, newQName, results);
    }

    private static String getStringToSearch(PsiElement element) {
        return switch (element) {
            case PsiJavaPackage javaPackage -> javaPackage.getQualifiedName();
            case PsiClass psiClass -> psiClass.getQualifiedName();
            case PsiDirectory directory -> getStringToSearch(JavaDirectoryService.getInstance().getPackage(directory));
            default -> {
                LOG.error("Unknown element type");
                yield null;
            }
        };
    }

    // Does not process non-code usages!
    @RequiredWriteAction
    public static PsiJavaPackage doMovePackage(PsiJavaPackage aPackage, MoveDestination moveDestination)
        throws IncorrectOperationException {
        PackageWrapper targetPackage = moveDestination.getTargetPackage();

        String newPrefix;
        if ("".equals(targetPackage.getQualifiedName())) {
            newPrefix = "";
        }
        else {
            newPrefix = targetPackage.getQualifiedName() + ".";
        }

        String newPackageQualifiedName = newPrefix + aPackage.getName();

        // do actual move
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(aPackage.getProject());
        PsiDirectory[] dirs = aPackage.getDirectories(projectScope);
        for (PsiDirectory dir : dirs) {
            PsiDirectory targetDirectory = moveDestination.getTargetDirectory(dir);
            if (targetDirectory != null) {
                moveDirectoryRecursively(dir, targetDirectory);
            }
        }

        aPackage.handleQualifiedNameChange(newPackageQualifiedName);

        return JavaPsiFacade.getInstance(targetPackage.getManager().getProject()).findPackage(newPackageQualifiedName);
    }

    @RequiredWriteAction
    public static void moveDirectoryRecursively(PsiDirectory dir, PsiDirectory destination)
        throws IncorrectOperationException {
        if (dir.getParentDirectory() == destination) {
            return;
        }
        moveDirectoryRecursively(dir, destination, new HashSet<>());
    }

    @RequiredWriteAction
    private static void moveDirectoryRecursively(PsiDirectory dir, PsiDirectory destination, Set<VirtualFile> movedPaths)
        throws IncorrectOperationException {
        VirtualFile destVFile = destination.getVirtualFile();
        VirtualFile sourceVFile = dir.getVirtualFile();
        if (movedPaths.contains(sourceVFile)) {
            return;
        }
        String targetName = dir.getName();
        PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(dir);
        if (aPackage != null) {
            String sourcePackageName = aPackage.getName();
            if (!sourcePackageName.equals(targetName)) {
                targetName = sourcePackageName;
            }
        }
        PsiDirectory subdirectoryInDest;
        boolean isSourceRoot = RefactoringUtil.isSourceRoot(dir);
        if (VirtualFileUtil.isAncestor(sourceVFile, destVFile, false) || isSourceRoot) {
            PsiDirectory exitsingSubdir = destination.findSubdirectory(targetName);
            if (exitsingSubdir == null) {
                subdirectoryInDest = destination.createSubdirectory(targetName);
                movedPaths.add(subdirectoryInDest.getVirtualFile());
            }
            else {
                subdirectoryInDest = exitsingSubdir;
            }
        }
        else {
            subdirectoryInDest = destination.findSubdirectory(targetName);
        }

        if (subdirectoryInDest == null) {
            VirtualFile virtualFile = dir.getVirtualFile();
            MoveFilesOrDirectoriesUtil.doMoveDirectory(dir, destination);
            movedPaths.add(virtualFile);
        }
        else {
            PsiFile[] files = dir.getFiles();
            for (PsiFile file : files) {
                try {
                    subdirectoryInDest.checkAdd(file);
                }
                catch (IncorrectOperationException e) {
                    continue;
                }
                MoveFilesOrDirectoriesUtil.doMoveFile(file, subdirectoryInDest);
            }

            PsiDirectory[] subdirectories = dir.getSubdirectories();
            for (PsiDirectory subdirectory : subdirectories) {
                if (!subdirectory.equals(subdirectoryInDest)) {
                    moveDirectoryRecursively(subdirectory, subdirectoryInDest, movedPaths);
                }
            }
            if (!isSourceRoot && dir.getFiles().length == 0 && dir.getSubdirectories().length == 0) {
                dir.delete();
            }
        }
    }

    public static void prepareMoveClass(PsiClass aClass) {
        aClass.getApplication().getExtensionPoint(MoveClassHandler.class)
            .forEach(handler -> handler.prepareMove(aClass));
    }

    public static void finishMoveClass(PsiClass aClass) {
        aClass.getApplication().getExtensionPoint(MoveClassHandler.class)
            .forEach(handler -> handler.finishMoveClass(aClass));
    }

    // Does not process non-code usages!
    @RequiredWriteAction
    public static PsiClass doMoveClass(PsiClass aClass, PsiDirectory moveDestination) throws IncorrectOperationException {
        return doMoveClass(aClass, moveDestination, true);
    }

    // Does not process non-code usages!
    @RequiredWriteAction
    public static PsiClass doMoveClass(PsiClass aClass, PsiDirectory moveDestination, boolean moveAllClassesInFile)
        throws IncorrectOperationException {
        PsiClass newClass;
        if (!moveAllClassesInFile) {
            newClass = aClass.getApplication().getExtensionPoint(MoveClassHandler.class)
                .computeSafeIfAny(handler -> handler.doMoveClass(aClass, moveDestination));
            if (newClass != null) {
                return newClass;
            }
        }

        PsiFile file = aClass.getContainingFile();
        PsiJavaPackage newPackage = JavaDirectoryService.getInstance().getPackage(moveDestination);

        newClass = aClass;
        if (!moveDestination.equals(file.getContainingDirectory())) {
            LOG.assertTrue(file.getVirtualFile() != null, aClass);
            MoveFilesOrDirectoriesUtil.doMoveFile(file, moveDestination);
            if (file instanceof PsiClassOwner classOwner && newPackage != null /*&& !JspPsiUtil.isInJspFile(file)*/) {
                // Do not rely on class instance identity retention after setPackageName (Scala)
                String aClassName = aClass.getName();
                classOwner.setPackageName(newPackage.getQualifiedName());
                newClass = findClassByName(classOwner, aClassName);
                LOG.assertTrue(newClass != null);
            }
        }
        return newClass;
    }

    @Nullable
    @RequiredReadAction
    private static PsiClass findClassByName(PsiClassOwner file, String name) {
        PsiClass[] classes = file.getClasses();
        for (PsiClass aClass : classes) {
            if (name.equals(aClass.getName())) {
                return aClass;
            }
        }
        return null;
    }

    public static String getPackageName(PackageWrapper aPackage) {
        if (aPackage == null) {
            return null;
        }
        String name = aPackage.getQualifiedName();
        if (name.length() > 0) {
            return name;
        }
        else {
            return UsageLocalize.defaultPackagePresentableName().get();
        }
    }

    @Nullable
    @RequiredUIAccess
    public static PsiDirectory chooseDestinationPackage(@Nonnull Project project, String packageName, @Nullable PsiDirectory baseDir) {
        PsiManager psiManager = PsiManager.getInstance(project);
        PackageWrapper packageWrapper = new PackageWrapper(psiManager, packageName);
        PsiJavaPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
        PsiDirectory directory;

        PsiDirectory[] directories = aPackage != null ? aPackage.getDirectories() : null;
        ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        boolean filterOutSources = baseDir != null && fileIndex.isInTestSourceContent(baseDir.getVirtualFile());
        if (directories != null && directories.length == 1 && !(filterOutSources &&
            !fileIndex.isInTestSourceContent(directories[0].getVirtualFile()))) {
            directory = directories[0];
        }
        else {
            VirtualFile[] contentSourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
            if (contentSourceRoots.length == 1 && !(filterOutSources && !fileIndex.isInTestSourceContent(contentSourceRoots[0]))) {
                directory = project.getApplication().runWriteAction(
                    (Supplier<PsiDirectory>)() -> RefactoringUtil.createPackageDirectoryInSourceRoot(packageWrapper, contentSourceRoots[0])
                );
            }
            else {
                VirtualFile sourceRootForFile = chooseSourceRoot(packageWrapper, contentSourceRoots, baseDir);
                if (sourceRootForFile == null) {
                    return null;
                }
                directory = project.getApplication().runWriteAction(
                    (Supplier<PsiDirectory>)() -> new AutocreatingSingleSourceRootMoveDestination(packageWrapper, sourceRootForFile)
                        .getTargetDirectory((PsiDirectory)null)
                );
            }
        }
        return directory;
    }

    @RequiredReadAction
    public static VirtualFile chooseSourceRoot(
        PackageWrapper targetPackage,
        VirtualFile[] contentSourceRoots,
        PsiDirectory initialDirectory
    ) {
        Project project = targetPackage.getManager().getProject();
        //ensure that there would be no duplicates: e.g. when one content root is subfolder of another root (configured via excluded roots)
        Set<PsiDirectory> targetDirectories = new LinkedHashSet<>();
        Map<PsiDirectory, String> relativePathsToCreate = new HashMap<>();
        buildDirectoryList(targetPackage, contentSourceRoots, targetDirectories, relativePathsToCreate);

        PsiDirectory selectedDirectory = DirectoryChooserUtil.chooseDirectory(
            targetDirectories.toArray(new PsiDirectory[targetDirectories.size()]),
            initialDirectory,
            project,
            relativePathsToCreate
        );

        if (selectedDirectory == null) {
            return null;
        }
        VirtualFile virt = selectedDirectory.getVirtualFile();
        VirtualFile sourceRootForFile = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(virt);
        LOG.assertTrue(sourceRootForFile != null);
        return sourceRootForFile;
    }

    @RequiredReadAction
    public static void buildDirectoryList(
        PackageWrapper aPackage,
        VirtualFile[] contentSourceRoots,
        Set<PsiDirectory> targetDirectories,
        Map<PsiDirectory, String> relativePathsToCreate
    ) {
        sourceRoots:
        for (VirtualFile root : contentSourceRoots) {
            PsiDirectory[] directories = aPackage.getDirectories();
            for (PsiDirectory directory : directories) {
                if (VirtualFileUtil.isAncestor(root, directory.getVirtualFile(), false)) {
                    targetDirectories.add(directory);
                    continue sourceRoots;
                }
            }
            String qNameToCreate;
            try {
                qNameToCreate = RefactoringUtil.qNameToCreateInSourceRoot(aPackage, root);
            }
            catch (IncorrectOperationException e) {
                continue sourceRoots;
            }
            PsiDirectory currentDirectory = aPackage.getManager().findDirectory(root);
            if (currentDirectory == null) {
                continue;
            }
            String[] shortNames = qNameToCreate.split("\\.");
            for (int j = 0; j < shortNames.length; j++) {
                String shortName = shortNames[j];
                PsiDirectory subdirectory = currentDirectory.findSubdirectory(shortName);
                if (subdirectory == null) {
                    targetDirectories.add(currentDirectory);
                    StringBuffer postfix = new StringBuffer();
                    for (int k = j; k < shortNames.length; k++) {
                        String name = shortNames[k];
                        postfix.append(File.separatorChar);
                        postfix.append(name);
                    }
                    relativePathsToCreate.put(currentDirectory, postfix.toString());
                    continue sourceRoots;
                }
                else {
                    currentDirectory = subdirectory;
                }
            }
        }
        LOG.assertTrue(targetDirectories.size() <= contentSourceRoots.length);
        LOG.assertTrue(relativePathsToCreate.size() <= contentSourceRoots.length);
    }
}
