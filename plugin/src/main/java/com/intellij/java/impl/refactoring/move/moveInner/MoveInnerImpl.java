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

/**
 * created at Nov 12, 2001
 * @author Jeka
 */
package com.intellij.java.impl.refactoring.move.moveInner;

import com.intellij.java.impl.codeInsight.PackageChooserDialog;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.move.MoveCallback;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import jakarta.annotation.Nullable;

public class MoveInnerImpl {
  private static final Logger LOG = Logger.getInstance(MoveInnerImpl.class);

  public static final String REFACTORING_NAME = RefactoringBundle.message("move.inner.to.upper.level.title");

  public static void doMove(final Project project, PsiElement[] elements, final MoveCallback moveCallback) {
    if (elements.length != 1) return;
    final PsiClass aClass = (PsiClass) elements[0];
    boolean condition = aClass.getContainingClass() != null;
    LOG.assertTrue(condition);

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;
    final PsiElement targetContainer = getTargetContainer(aClass, true);
    if (targetContainer == null) return;

    final MoveInnerDialog dialog = new MoveInnerDialog(
            project,
            aClass,
            new MoveInnerProcessor(project, moveCallback),
            targetContainer);
    dialog.show();

  }

  /**
   * must be called in atomic action
   */
  @Nullable
  public static PsiElement getTargetContainer(PsiClass innerClass, final boolean chooseIfNotUnderSource) {
    final PsiClass outerClass = innerClass.getContainingClass();
    assert outerClass != null; // Only inner classes allowed.

    PsiElement outerClassParent = outerClass.getParent();
    while (outerClassParent != null) {
      if (outerClassParent instanceof PsiClass && !(outerClassParent instanceof PsiAnonymousClass)) {
        return outerClassParent;
      }
      else if (outerClassParent instanceof PsiFile) {
        final PsiDirectory directory = innerClass.getContainingFile().getContainingDirectory();
        final PsiJavaPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        if (aPackage == null) {
          if (chooseIfNotUnderSource) {
            PackageChooserDialog chooser = new PackageChooserDialog("Select Target Package", innerClass.getProject());
            chooser.show();
            if (!chooser.isOK()) return null;
            final PsiJavaPackage chosenPackage = chooser.getSelectedPackage();
            if (chosenPackage == null) return null;
            return chosenPackage.getDirectories()[0];
          }

          return null;
        }
        return directory;
      }
      outerClassParent = outerClassParent.getParent();
    }
    // should not happen
    LOG.assertTrue(false);
    return null;
  }
}
