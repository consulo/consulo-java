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
package com.intellij.java.impl.refactoring.rename;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.rename.RenamePsiElementProcessor;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Map;

/**
 * @author yole
 */
@ExtensionImpl
public class RenamePsiPackageProcessor extends RenamePsiElementProcessor {
    @Override
    public boolean canProcessElement(@Nonnull PsiElement element) {
        return element instanceof PsiJavaPackage;
    }

    @Override
    @RequiredWriteAction
    public void renameElement(
        PsiElement element,
        String newName,
        UsageInfo[] usages,
        @Nullable RefactoringElementListener listener
    ) throws IncorrectOperationException {
        PsiJavaPackage psiPackage = (PsiJavaPackage) element;
        psiPackage.handleQualifiedNameChange(PsiUtilCore.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), newName));
        RenameUtil.doRenameGenericNamedElement(element, newName, usages, listener);
    }

    @Override
    public String getQualifiedNameAfterRename(PsiElement element, String newName, boolean nonJava) {
        return getPackageQualifiedNameAfterRename((PsiJavaPackage) element, newName, nonJava);
    }

    public static String getPackageQualifiedNameAfterRename(PsiJavaPackage element, String newName, boolean nonJava) {
        if (nonJava) {
            String qName = element.getQualifiedName();
            int index = qName.lastIndexOf('.');
            return index < 0 ? newName : qName.substring(0, index + 1) + newName;
        }
        else {
            return newName;
        }
    }

    @Override
    public void findExistingNameConflicts(@Nonnull PsiElement element, String newName, MultiMap<PsiElement, LocalizeValue> conflicts) {
        PsiJavaPackage aPackage = (PsiJavaPackage) element;
        Project project = element.getProject();
        String qualifiedNameAfterRename = getPackageQualifiedNameAfterRename(aPackage, newName, true);
        PsiClass psiClass =
            JavaPsiFacade.getInstance(project).findClass(qualifiedNameAfterRename, GlobalSearchScope.allScope(project));
        if (psiClass != null) {
            conflicts.putValue(
                psiClass,
                LocalizeValue.localizeTODO("Class with qualified name \'" + qualifiedNameAfterRename + "\'  already exist")
            );
        }
    }

    @Override
    public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames) {
        preparePackageRenaming((PsiJavaPackage) element, newName, allRenames);
    }

    public static void preparePackageRenaming(PsiJavaPackage psiPackage, String newName, Map<PsiElement, String> allRenames) {
        PsiDirectory[] directories = psiPackage.getDirectories();
        for (PsiDirectory directory : directories) {
            if (!JavaDirectoryService.getInstance().isSourceRoot(directory)) {
                allRenames.put(directory, newName);
            }
        }
    }

    @Nullable
    @Override
    public Runnable getPostRenameCallback(@Nonnull PsiElement element, String newName, RefactoringElementListener listener) {
        Project project = element.getProject();
        PsiJavaPackage psiPackage = (PsiJavaPackage) element;
        String newQualifiedName = PsiUtilCore.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), newName);
        return () -> {
            PsiJavaPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(newQualifiedName);
            if (aPackage == null) {
                return; //rename failed e.g. when the dir is used by another app
            }
            listener.elementRenamed(aPackage);
        };
    }

    @Nullable
    @Override
    public String getHelpID(PsiElement element) {
        return HelpID.RENAME_PACKAGE;
    }

    @Override
    public boolean isToSearchInComments(PsiElement psiElement) {
        return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE;
    }

    @Override
    public void setToSearchInComments(PsiElement element, boolean enabled) {
        JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = enabled;
    }

    @Override
    public boolean isToSearchForTextOccurrences(PsiElement element) {
        return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE;
    }

    @Override
    public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
        JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = enabled;
    }
}
