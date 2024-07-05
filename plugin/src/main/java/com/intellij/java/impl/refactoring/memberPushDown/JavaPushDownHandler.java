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
package com.intellij.java.impl.refactoring.memberPushDown;

import com.intellij.java.language.psi.*;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.project.Project;
import consulo.language.psi.*;
import com.intellij.java.impl.refactoring.HelpID;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.classMember.MemberInfoBase;
import consulo.language.editor.refactoring.ElementsHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfoStorage;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author dsl
 */
public class JavaPushDownHandler implements RefactoringActionHandler, ElementsHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("push.members.down.title");

  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);

    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(
          RefactoringLocalize.theCaretShouldBePositionedInsideAClassToPushMembersFrom().get()
        );
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.MEMBERS_PUSH_DOWN);
        return;
      }

      if (element instanceof PsiClass || element instanceof PsiField || element instanceof PsiMethod) {
        /*if (element instanceof JspClass) {
          RefactoringMessageUtil.showNotSupportedForJspClassesError(project, editor, REFACTORING_NAME, HelpID.MEMBERS_PUSH_DOWN);
          return;
        }   */
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(@Nonnull final Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    PsiElement element = elements[0];
    PsiClass aClass;
    PsiElement aMember = null;

    if (element instanceof PsiClass) {
      aClass = (PsiClass) element;
    } else if (element instanceof PsiMethod) {
      aClass = ((PsiMethod) element).getContainingClass();
      aMember = element;
    } else if (element instanceof PsiField) {
      aClass = ((PsiField) element).getContainingClass();
      aMember = element;
    } else
      return;

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;
    MemberInfoStorage memberInfoStorage = new MemberInfoStorage(aClass, new MemberInfo.Filter<PsiMember>() {
      public boolean includeMember(PsiMember element) {
        return !(element instanceof PsiEnumConstant);
      }
    });
    List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(aClass);
    PsiManager manager = aClass.getManager();

    for (MemberInfoBase<PsiMember> member : members) {
      if (manager.areElementsEquivalent(member.getMember(), aMember)) {
        member.setChecked(true);
        break;
      }
    }
    PushDownDialog dialog = new PushDownDialog(
            project,
            members.toArray(new MemberInfo[members.size()]),
            aClass);
    dialog.show();
  }

  public boolean isEnabledOnElements(PsiElement[] elements) {
    /*
    if (elements.length == 1) {
      return elements[0] instanceof PsiClass || elements[0] instanceof PsiField || elements[0] instanceof PsiMethod;
    }
    else if (elements.length > 1){
      for (int  idx = 0;  idx < elements.length;  idx++) {
        PsiElement element = elements[idx];
        if (!(element instanceof PsiField || element instanceof PsiMethod)) return false;
      }
      return true;
    }
    return false;
    */
    // todo: multiple selection etc
    return elements.length == 1 && elements[0] instanceof PsiClass;
  }
}