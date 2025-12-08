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

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.access.RequiredReadAction;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.util.ModuleContentUtil;
import consulo.project.Project;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.java.impl.refactoring.MoveDestination;
import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.util.RefactoringConflictsUtil;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;

import java.util.Collection;

/**
 * @author dsl
 */
public class SingleSourceRootMoveDestination implements MoveDestination {
    private static final Logger LOG = Logger.getInstance(SingleSourceRootMoveDestination.class);
    private final PackageWrapper myPackage;
    private final PsiDirectory myTargetDirectory;

    public SingleSourceRootMoveDestination(PackageWrapper aPackage, PsiDirectory targetDirectory) {
        LOG.assertTrue(aPackage.equalToPackage(JavaDirectoryService.getInstance().getPackage(targetDirectory)));
        myPackage = aPackage;
        myTargetDirectory = targetDirectory;
    }

    @Override
    public PackageWrapper getTargetPackage() {
        return myPackage;
    }

    @Override
    public PsiDirectory getTargetIfExists(PsiDirectory source) {
        return myTargetDirectory;
    }

    @Override
    public PsiDirectory getTargetIfExists(PsiFile source) {
        return myTargetDirectory;
    }

    @Override
    public PsiDirectory getTargetDirectory(PsiDirectory source) {
        return myTargetDirectory;
    }

    @Override
    public String verify(PsiFile source) {
        return null;
    }

    @Override
    public String verify(PsiDirectory source) {
        return null;
    }

    @Override
    public String verify(PsiJavaPackage source) {
        return null;
    }

    @Override
    @RequiredReadAction
    public void analyzeModuleConflicts(
        Collection<PsiElement> elements,
        MultiMap<PsiElement, LocalizeValue> conflicts,
        UsageInfo[] usages
    ) {
        RefactoringConflictsUtil.analyzeModuleConflicts(
            myPackage.getManager().getProject(),
            elements,
            usages,
            myTargetDirectory,
            conflicts
        );
    }

    @Override
    public boolean isTargetAccessible(Project project, VirtualFile place) {
        boolean inTestSourceContent = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(place);
        Module module = ModuleContentUtil.findModuleForFile(place, project);
        VirtualFile targetVirtualFile = myTargetDirectory.getVirtualFile();
        return module == null
            || GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, inTestSourceContent).contains(targetVirtualFile);
    }

    @Override
    public PsiDirectory getTargetDirectory(PsiFile source) {
        return myTargetDirectory;
    }
}
