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
package com.intellij.java.impl.refactoring.extractInterface;

import consulo.application.Application;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPsiElementPointer;
import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import consulo.language.editor.refactoring.RefactoringBundle;
import com.intellij.java.impl.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;
import com.intellij.java.impl.refactoring.ui.YesNoPreviewUsagesDialog;

/**
 * @author dsl
 */
public class ExtractClassUtil {
  public static void askAndTurnRefsToSuper(final Project project,
                                           final SmartPsiElementPointer classPointer, 
                                           final SmartPsiElementPointer interfacePointer) {
    final PsiElement classElement = classPointer.getElement();
    final PsiElement interfaceElement = interfacePointer.getElement();
    if (classElement instanceof PsiClass && classElement.isValid() && interfaceElement instanceof PsiClass && interfaceElement.isValid()) {
      final PsiClass superClass = (PsiClass) interfaceElement;
      String superClassName = superClass.getName();
      String className = ((PsiClass) classElement).getName();
      String createdString = superClass.isInterface() ?
                             RefactoringBundle.message("interface.has.been.successfully.created", superClassName) :
                             RefactoringBundle.message("class.has.been.successfully.created", superClassName);
      String message = createdString + "\n" +
                       RefactoringBundle.message("use.super.references.prompt",
                           Application.get().getName().get(), className, superClassName);
      YesNoPreviewUsagesDialog dialog = new YesNoPreviewUsagesDialog(
        RefactoringBundle.message("analyze.and.replace.usages"),
        message,
        JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_PREVIEW_USAGES,
        /*HelpID.TURN_REFS_TO_SUPER*/null, project);
      dialog.show();
      if (dialog.isOK()) {
        final boolean isPreviewUsages = dialog.isPreviewUsages();
        JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_PREVIEW_USAGES = isPreviewUsages;
        TurnRefsToSuperProcessor processor =
                new TurnRefsToSuperProcessor(project, (PsiClass) classElement, superClass, true);
        processor.setPreviewUsages(isPreviewUsages);
        processor.run();
      }
    }
  }
}
