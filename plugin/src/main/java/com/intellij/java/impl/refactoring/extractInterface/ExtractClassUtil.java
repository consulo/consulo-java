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

import com.intellij.java.impl.refactoring.JavaRefactoringSettings;
import com.intellij.java.impl.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;
import com.intellij.java.impl.refactoring.ui.YesNoPreviewUsagesDialog;
import com.intellij.java.language.psi.PsiClass;
import consulo.application.Application;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * @author dsl
 */
public class ExtractClassUtil {
  @RequiredUIAccess
  public static void askAndTurnRefsToSuper(
    Project project,
    SmartPsiElementPointer classPointer,
    SmartPsiElementPointer interfacePointer
  ) {
    PsiElement classElement = classPointer.getElement();
    PsiElement interfaceElement = interfacePointer.getElement();
    if (classElement instanceof PsiClass psiClass && classElement.isValid()
      && interfaceElement instanceof PsiClass superClass && interfaceElement.isValid()) {
      String superClassName = superClass.getName();
      String className = psiClass.getName();
      LocalizeValue createdString = superClass.isInterface()
        ? RefactoringLocalize.interfaceHasBeenSuccessfullyCreated(superClassName)
        : RefactoringLocalize.classHasBeenSuccessfullyCreated(superClassName);
      String message = createdString + "\n" +
        RefactoringLocalize.useSuperReferencesPrompt(Application.get().getName().get(), className, superClassName);
      YesNoPreviewUsagesDialog dialog = new YesNoPreviewUsagesDialog(
        RefactoringLocalize.analyzeAndReplaceUsages().get(),
        message,
        JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_PREVIEW_USAGES,
        /*HelpID.TURN_REFS_TO_SUPER*/null, project);
      dialog.show();
      if (dialog.isOK()) {
        boolean isPreviewUsages = dialog.isPreviewUsages();
        JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_PREVIEW_USAGES = isPreviewUsages;
        TurnRefsToSuperProcessor processor =
                new TurnRefsToSuperProcessor(project, (PsiClass) classElement, superClass, true);
        processor.setPreviewUsages(isPreviewUsages);
        processor.run();
      }
    }
  }
}
