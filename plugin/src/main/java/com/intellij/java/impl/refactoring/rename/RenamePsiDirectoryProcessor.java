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
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.psi.PsiPackageStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.RefactoringSettings;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.rename.RenameDialog;
import consulo.language.editor.refactoring.rename.RenamePsiElementProcessor;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.ide.impl.idea.refactoring.rename.RenameWithOptionalReferencesDialog;
import consulo.usage.UsageInfo;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
@ExtensionImpl
public class RenamePsiDirectoryProcessor extends RenamePsiElementProcessor {
  public boolean canProcessElement(@Nonnull final PsiElement element) {
    return element instanceof PsiDirectory;
  }

  @Override
  public RenameDialog createRenameDialog(Project project, PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
    return new RenameWithOptionalReferencesDialog(project, element, nameSuggestionContext, editor) {
      protected boolean getSearchForReferences() {
        return RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY;
      }

      protected void setSearchForReferences(boolean value) {
        RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY = value;
      }
    };
  }

  public void renameElement(final PsiElement element,
                            final String newName,
                            final UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    PsiDirectory aDirectory = (PsiDirectory) element;
    // rename all non-package statement references
    for (UsageInfo usage : usages) {
      if (PsiTreeUtil.getParentOfType(usage.getElement(), PsiPackageStatement.class) != null) continue;
      RenameUtil.rename(usage, newName);
    }

    //rename package statement
    for (UsageInfo usage : usages) {
      if (PsiTreeUtil.getParentOfType(usage.getElement(), PsiPackageStatement.class) == null) continue;
      RenameUtil.rename(usage, newName);
    }

    aDirectory.setName(newName);
    if (listener != null) {
      listener.elementRenamed(aDirectory);
    }
  }

  public String getQualifiedNameAfterRename(final PsiElement element, final String newName, final boolean nonJava) {
    PsiJavaPackage psiPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory) element));
    if (psiPackage != null) {
      return RenamePsiPackageProcessor.getPackageQualifiedNameAfterRename(psiPackage, newName, nonJava);
    }
    return newName;
  }

  @Nonnull
  @Override
  public Collection<PsiReference> findReferences(PsiElement element) {
    if (!RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY) {
      return Collections.emptyList();
    }
    return ReferencesSearch.search(element).findAll();
  }

  @Nullable
  @Override
  public PsiElement getElementToSearchInStringsAndComments(PsiElement element) {
    final PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory) element);
    if (aPackage != null) return aPackage;
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpID(final PsiElement element) {
    return HelpID.RENAME_DIRECTORY;
  }

  public boolean isToSearchInComments(PsiElement element) {
    element = JavaDirectoryService.getInstance().getPackage(((PsiDirectory) element));
    if (element == null) return false;
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE;
  }

  public void setToSearchInComments(PsiElement element, final boolean enabled) {
    element = JavaDirectoryService.getInstance().getPackage(((PsiDirectory) element));
    if (element != null) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = enabled;
    }
  }

  public boolean isToSearchForTextOccurrences(PsiElement element) {
    element = JavaDirectoryService.getInstance().getPackage(((PsiDirectory) element));
    if (element == null) return false;
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE;
  }

  public void setToSearchForTextOccurrences(PsiElement element, final boolean enabled) {
    element = JavaDirectoryService.getInstance().getPackage(((PsiDirectory) element));
    if (element != null) {
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = enabled;
    }
  }
}
