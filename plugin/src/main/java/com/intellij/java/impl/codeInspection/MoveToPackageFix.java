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
package com.intellij.java.impl.codeInspection;

import consulo.application.CommonBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import com.intellij.java.impl.refactoring.PackageWrapper;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.java.impl.refactoring.util.RefactoringMessageUtil;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.application.ApplicationManager;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;

public class MoveToPackageFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(MoveToPackageFix.class);
  private final String myTargetPackage;

  public MoveToPackageFix(String targetPackage) {
    myTargetPackage = targetPackage;
  }

  @Override
  @Nonnull
  public String getName() {
    return JavaQuickFixBundle.message("move.class.to.package.text", myTargetPackage);
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return JavaQuickFixBundle.message("move.class.to.package.family");
  }

  public boolean isAvailable(PsiFile myFile) {
    return myFile != null
        && myFile.isValid()
        && myFile.getManager().isInProject(myFile)
        && myFile instanceof PsiJavaFile
        && ((PsiJavaFile) myFile).getClasses().length != 0
        && myTargetPackage != null;
  }

  @Override
  public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element == null) return;
    final PsiFile myFile = element.getContainingFile();

    if (!FileModificationService.getInstance().prepareFileForWrite(myFile)) return;

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        chooseDirectoryAndMove(project, myFile);
      }
    });
  }

  private void chooseDirectoryAndMove(Project project, PsiFile myFile) {
    try {
      PsiDirectory directory = MoveClassesOrPackagesUtil.chooseDestinationPackage(project, myTargetPackage, myFile.getContainingDirectory());

      if (directory == null) {
        return;
      }
      String error = RefactoringMessageUtil.checkCanCreateFile(directory, myFile.getName());
      if (error != null) {
        Messages.showMessageDialog(project, error, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        return;
      }
      new MoveClassesOrPackagesProcessor(
          project,
          ((PsiJavaFile) myFile).getClasses(),
          new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(directory)), directory), false,
          false,
          null).run();
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }


}
