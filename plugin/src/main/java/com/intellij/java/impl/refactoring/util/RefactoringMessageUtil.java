
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
package com.intellij.java.impl.refactoring.util;

import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDirectory;
import consulo.project.Project;
import consulo.usage.UsageViewUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

public class RefactoringMessageUtil {

  public static String getIncorrectIdentifierMessage(String identifierName) {
    return RefactoringLocalize.zeroIsNotALegalJavaIdentifier(identifierName).get();
  }

  /**
   * @return null, if can create a class
   * an error message, if cannot create a class
   */
  public static String checkCanCreateClass(PsiDirectory destinationDirectory, String className) {
    PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(destinationDirectory);
    VirtualFile file = destinationDirectory.getVirtualFile();
    for (PsiClass aClass : classes) {
      if (className.equals(aClass.getName())) {
        return RefactoringLocalize.directory0AlreadyContains1Named2(
            file.getPresentableUrl(),
            UsageViewUtil.getType(aClass),
            className
          ).get();
      }
    }
    @NonNls String fileName = className + ".java";
    return checkCanCreateFile(destinationDirectory, fileName);
  }

  public static String checkCanCreateFile(PsiDirectory destinationDirectory, String fileName) {
    VirtualFile file = destinationDirectory.getVirtualFile();
    VirtualFile child = file.findChild(fileName);
    if (child != null) {
      return RefactoringLocalize.directory0AlreadyContainsAFileNamed1(file.getPresentableUrl(), fileName).get();
    }
    return null;
  }

  public static String getGetterSetterMessage(String newName, String action, PsiMethod getter, PsiMethod setter) {
    if (getter != null && setter != null) {
      return RefactoringLocalize.getterAndSetterMethodsFoundForTheField0(newName, action).get();
    } else if (getter != null) {
      return RefactoringLocalize.getterMethodFoundForTheField0(newName, action).get();
    } else {
      return RefactoringLocalize.setterMethodFoundForTheField0(newName, action).get();
    }
  }

  public static void showNotSupportedForJspClassesError(final Project project, Editor editor, final String refactoringName, final String helpId) {
    String message = RefactoringBundle.getCannotRefactorMessage(RefactoringLocalize.refactoringIsNotSupportedForJspClasses().get());
    CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, helpId);
  }
}