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
 * created at Oct 25, 2001
 * @author Jeka
 */
package com.intellij.java.impl.refactoring.turnRefsToSuper;

import java.util.ArrayList;

import jakarta.annotation.Nonnull;

import consulo.dataContext.DataContext;
import consulo.language.editor.PlatformDataKeys;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.impl.refactoring.HelpID;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import com.intellij.java.impl.refactoring.util.RefactoringHierarchyUtil;

public class TurnRefsToSuperHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("use.interface.where.possible.title");


  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.class"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.TURN_REFS_TO_SUPER);
        return;
      }
      if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(@Nonnull final Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

        PsiClass subClass = (PsiClass) elements[0];

    ArrayList basesList = RefactoringHierarchyUtil.createBasesList(subClass, true, true);

    if (basesList.isEmpty()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("interface.does.not.have.base.interfaces", subClass.getQualifiedName()));
      Editor editor = dataContext.getData(PlatformDataKeys.EDITOR);
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.TURN_REFS_TO_SUPER);
      return;
    }

    new TurnRefsToSuperDialog(project, subClass, basesList).show();
  }

}
