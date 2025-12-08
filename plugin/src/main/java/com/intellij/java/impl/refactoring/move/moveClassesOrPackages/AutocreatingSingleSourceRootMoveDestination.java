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

import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.util.RefactoringConflictsUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * @author dsl
 */
public class AutocreatingSingleSourceRootMoveDestination extends AutocreatingMoveDestination {
    private final VirtualFile mySourceRoot;

    public AutocreatingSingleSourceRootMoveDestination(PackageWrapper targetPackage, @Nonnull VirtualFile sourceRoot) {
        super(targetPackage);
        mySourceRoot = sourceRoot;
    }

    @Override
    public PackageWrapper getTargetPackage() {
        return myPackage;
    }

    @Override
    public PsiDirectory getTargetIfExists(PsiDirectory source) {
        return RefactoringUtil.findPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
    }

    @Override
    public PsiDirectory getTargetIfExists(PsiFile source) {
        return RefactoringUtil.findPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
    }

    @Override
    public PsiDirectory getTargetDirectory(PsiDirectory source) throws IncorrectOperationException {
        return getDirectory();
    }

    @Override
    public PsiDirectory getTargetDirectory(PsiFile source) throws IncorrectOperationException {
        return getDirectory();
    }

    @Nullable
    @Override
    public String verify(PsiFile source) {
        return checkCanCreateInSourceRoot(mySourceRoot);
    }

    @Override
    public String verify(PsiDirectory source) {
        return checkCanCreateInSourceRoot(mySourceRoot);
    }

    @Override
    public String verify(PsiJavaPackage aPackage) {
        return checkCanCreateInSourceRoot(mySourceRoot);
    }

    @Override
    @RequiredReadAction
    public void analyzeModuleConflicts(
        Collection<PsiElement> elements,
        MultiMap<PsiElement, LocalizeValue> conflicts,
        UsageInfo[] usages
    ) {
        RefactoringConflictsUtil.analyzeModuleConflicts(
            getTargetPackage().getManager().getProject(),
            elements,
            usages,
            mySourceRoot,
            conflicts
        );
    }

    @Override
    public boolean isTargetAccessible(Project project, VirtualFile place) {
        boolean inTestSourceContent = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(place);
        Module module = ModuleUtilCore.findModuleForFile(place, project);
        return mySourceRoot == null
            || module == null
            || GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, inTestSourceContent).contains(mySourceRoot);
    }

    PsiDirectory myTargetDirectory = null;

    private PsiDirectory getDirectory() throws IncorrectOperationException {
        if (myTargetDirectory == null) {
            myTargetDirectory = RefactoringUtil.createPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
        }
        return RefactoringUtil.createPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
    }
}
