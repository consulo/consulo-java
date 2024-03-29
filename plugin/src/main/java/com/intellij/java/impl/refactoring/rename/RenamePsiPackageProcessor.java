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
import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.PsiUtilCore;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.rename.RenamePsiElementProcessor;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.usage.UsageInfo;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.MultiMap;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * @author yole
 */
@ExtensionImpl
public class RenamePsiPackageProcessor extends RenamePsiElementProcessor {
  private final Logger LOG = Logger.getInstance(RenamePsiPackageProcessor.class);

  public boolean canProcessElement(@Nonnull final PsiElement element) {
    return element instanceof PsiJavaPackage;
  }

  public void renameElement(final PsiElement element,
                            final String newName,
                            final UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    final PsiJavaPackage psiPackage = (PsiJavaPackage) element;
    psiPackage.handleQualifiedNameChange(PsiUtilCore.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), newName));
    RenameUtil.doRenameGenericNamedElement(element, newName, usages, listener);
  }

  public String getQualifiedNameAfterRename(final PsiElement element, final String newName, final boolean nonJava) {
    return getPackageQualifiedNameAfterRename((PsiJavaPackage) element, newName, nonJava);
  }

  public static String getPackageQualifiedNameAfterRename(final PsiJavaPackage element, final String newName, final boolean nonJava) {
    if (nonJava) {
      String qName = element.getQualifiedName();
      int index = qName.lastIndexOf('.');
      return index < 0 ? newName : qName.substring(0, index + 1) + newName;
    } else {
      return newName;
    }
  }

  @Override
  public void findExistingNameConflicts(PsiElement element, String newName, MultiMap<PsiElement, String> conflicts) {
    final PsiJavaPackage aPackage = (PsiJavaPackage) element;
    final Project project = element.getProject();
    final String qualifiedNameAfterRename = getPackageQualifiedNameAfterRename(aPackage, newName, true);
    final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedNameAfterRename, GlobalSearchScope.allScope(project));
    if (psiClass != null) {
      conflicts.putValue(psiClass, "Class with qualified name \'" + qualifiedNameAfterRename + "\'  already exist");
    }
  }

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    preparePackageRenaming((PsiJavaPackage) element, newName, allRenames);
  }

  public static void preparePackageRenaming(PsiJavaPackage psiPackage, final String newName, Map<PsiElement, String> allRenames) {
    final PsiDirectory[] directories = psiPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      if (!JavaDirectoryService.getInstance().isSourceRoot(directory)) {
        allRenames.put(directory, newName);
      }
    }
  }

  @Nullable
  public Runnable getPostRenameCallback(final PsiElement element, final String newName, final RefactoringElementListener listener) {
    final Project project = element.getProject();
    final PsiJavaPackage psiPackage = (PsiJavaPackage) element;
    final String newQualifiedName = PsiUtilCore.getQualifiedNameAfterRename(psiPackage.getQualifiedName(), newName);
    return new Runnable() {
      public void run() {
        final PsiJavaPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(newQualifiedName);
        if (aPackage == null) {
          return; //rename failed e.g. when the dir is used by another app
        }
        listener.elementRenamed(aPackage);
      }
    };
  }

  @Nullable
  @NonNls
  public String getHelpID(final PsiElement element) {
    return HelpID.RENAME_PACKAGE;
  }

  public boolean isToSearchInComments(final PsiElement psiElement) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE;
  }

  public void setToSearchInComments(final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = enabled;
  }

  public boolean isToSearchForTextOccurrences(final PsiElement element) {
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE;
  }

  public void setToSearchForTextOccurrences(final PsiElement element, final boolean enabled) {
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = enabled;
  }
}
