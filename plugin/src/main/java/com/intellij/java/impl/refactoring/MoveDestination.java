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
package com.intellij.java.impl.refactoring;

import consulo.annotation.access.RequiredReadAction;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.usage.UsageInfo;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nullable;

import java.lang.String;
import java.util.Collection;

/**
 * Represents a destination of Move Classes/Packages refactoring.
 * Destination of Move refactoring is generally a single package,
 * and various <code>MoveDestination</code>s control how moved items
 * will be layouted in directories corresponding to target packages.
 *
 * Instances of this interface can be obtained via methods of {@link RefactoringFactory}.
 *
 * @author dsl
 * @see JavaRefactoringFactory#createSourceFolderPreservingMoveDestination(String)
 * @see JavaRefactoringFactory#createSourceRootMoveDestination(String, com.intellij.openapi.vfs.VirtualFile)
 */
public interface MoveDestination {
    /**
     * Invoked in command & write action
     */
    PsiDirectory getTargetDirectory(PsiDirectory source) throws IncorrectOperationException;

    /**
     * Invoked in command & write action
     */
    PsiDirectory getTargetDirectory(PsiFile source) throws IncorrectOperationException;

    PackageWrapper getTargetPackage();

    PsiDirectory getTargetIfExists(PsiDirectory source);

    PsiDirectory getTargetIfExists(PsiFile source);

    @Nullable
    String verify(PsiFile source);

    @Nullable
    String verify(PsiDirectory source);

    @Nullable
    String verify(PsiJavaPackage source);

    @RequiredReadAction
    void analyzeModuleConflicts(Collection<PsiElement> elements, MultiMap<PsiElement, LocalizeValue> conflicts, UsageInfo[] usages);

    boolean isTargetAccessible(Project project, VirtualFile place);
}
