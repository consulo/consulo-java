package com.intellij.java.impl.refactoring.move.moveClassesOrPackages;

import com.intellij.java.impl.refactoring.util.RefactoringConflictsUtil;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;

import java.util.*;
import java.util.function.Function;

@ExtensionImpl
public class JavaMoveDirectoryWithClassesHelper extends MoveDirectoryWithClassesHelper {

    @Override
    public void findUsages(
        Collection<PsiFile> filesToMove,
        PsiDirectory[] directoriesToMove,
        Collection<UsageInfo> usages,
        boolean searchInComments,
        boolean searchInNonJavaFiles,
        Project project
    ) {
        final Set<String> packageNames = new HashSet<String>();
        for (PsiFile psiFile : filesToMove) {
            if (psiFile instanceof PsiClassOwner) {
                final PsiClass[] classes = ((PsiClassOwner)psiFile).getClasses();
                for (PsiClass aClass : classes) {
                    Collections
                        .addAll(
                            usages,
                            MoveClassesOrPackagesUtil.findUsages(aClass, searchInComments, searchInNonJavaFiles, aClass.getName())
                        );
                }
                packageNames.add(((PsiClassOwner)psiFile).getPackageName());
            }
        }

        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        for (String packageName : packageNames) {
            final PsiJavaPackage aPackage = psiFacade.findPackage(packageName);
            if (aPackage != null) {
                boolean remainsNothing = true;
                for (PsiDirectory packageDirectory : aPackage.getDirectories()) {
                    if (!isUnderRefactoring(packageDirectory, directoriesToMove)) {
                        remainsNothing = false;
                        break;
                    }
                }
                if (remainsNothing) {
                    for (PsiReference reference : ReferencesSearch.search(aPackage)) {
                        final PsiElement element = reference.getElement();
                        final PsiImportStatementBase statementBase = PsiTreeUtil.getParentOfType(element, PsiImportStatementBase.class);
                        if (statementBase != null && statementBase.isOnDemand()) {
                            usages.add(new RemoveOnDemandImportStatementsUsageInfo(statementBase));
                        }
                    }
                }
            }
        }
    }

    private static boolean isUnderRefactoring(PsiDirectory packageDirectory, PsiDirectory[] directoriesToMove) {
        for (PsiDirectory directory : directoriesToMove) {
            if (PsiTreeUtil.isAncestor(directory, packageDirectory, true)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean move(
        PsiFile file,
        PsiDirectory moveDestination,
        Map<PsiElement, PsiElement> oldToNewElementsMapping,
        List<PsiFile> movedFiles,
        RefactoringElementListener listener
    ) {
        if (!(file instanceof PsiClassOwner)) {
            return false;
        }

        //if (!JspPsiUtil.isInJspFile(file)) {
        return false;
        /*}

        for (PsiClass psiClass : ((PsiClassOwner)file).getClasses()) {
            final PsiClass newClass = MoveClassesOrPackagesUtil.doMoveClass(psiClass, moveDestination);
            oldToNewElementsMapping.put(psiClass, newClass);
            listener.elementMoved(newClass);
        }
        return true;*/
    }

    @Override
    public void postProcessUsages(UsageInfo[] usages, Function<PsiDirectory, PsiDirectory> newDirMapper) {
        for (UsageInfo usage : usages) {
            if (usage instanceof RemoveOnDemandImportStatementsUsageInfo) {
                final PsiElement element = usage.getElement();
                if (element != null) {
                    element.delete();
                }
            }
        }
    }

    @Override
    public void preprocessUsages(
        Project project,
        Set<PsiFile> files,
        UsageInfo[] infos,
        PsiDirectory directory,
        MultiMap<PsiElement, String> conflicts
    ) {
        RefactoringConflictsUtil.analyzeModuleConflicts(project, files, infos, directory, conflicts);
    }

    @Override
    public void beforeMove(PsiFile psiFile) {
        ChangeContextUtil.encodeContextInfo(psiFile, true);
    }

    @Override
    public void afterMove(PsiElement newElement) {
        ChangeContextUtil.decodeContextInfo(newElement, null, null);
    }

    private static class RemoveOnDemandImportStatementsUsageInfo extends UsageInfo {
        public RemoveOnDemandImportStatementsUsageInfo(PsiImportStatementBase statementBase) {
            super(statementBase);
        }
    }
}
