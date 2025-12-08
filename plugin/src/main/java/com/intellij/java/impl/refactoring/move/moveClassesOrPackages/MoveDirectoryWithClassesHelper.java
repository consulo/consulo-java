package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * @author ksafonov
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class MoveDirectoryWithClassesHelper {
    private static final ExtensionPointName<MoveDirectoryWithClassesHelper> EP_NAME =
        ExtensionPointName.create(MoveDirectoryWithClassesHelper.class);

    public abstract void findUsages(
        Collection<PsiFile> filesToMove,
        PsiDirectory[] directoriesToMove,
        Collection<UsageInfo> result,
        boolean searchInComments, boolean searchInNonJavaFiles, Project project
    );

    public abstract boolean move(
        PsiFile file,
        PsiDirectory moveDestination,
        Map<PsiElement, PsiElement> oldToNewElementsMapping,
        List<PsiFile> movedFiles,
        RefactoringElementListener listener
    );

    public abstract void postProcessUsages(UsageInfo[] usages, Function<PsiDirectory, PsiDirectory> newDirMapper);

    public abstract void beforeMove(PsiFile psiFile);

    public abstract void afterMove(PsiElement newElement);

    public void preprocessUsages(
        Project project,
        Set<PsiFile> files,
        UsageInfo[] infos,
        PsiDirectory directory,
        MultiMap<PsiElement, LocalizeValue> conflicts
    ) {
    }

    public static List<MoveDirectoryWithClassesHelper> findAll() {
        return EP_NAME.getExtensionList();
    }
}