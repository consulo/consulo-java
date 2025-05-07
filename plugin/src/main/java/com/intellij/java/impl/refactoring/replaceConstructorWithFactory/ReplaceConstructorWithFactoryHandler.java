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
package com.intellij.java.impl.refactoring.replaceConstructorWithFactory;

import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.awt.UIUtil;
import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.language.psi.*;
import com.intellij.java.impl.refactoring.HelpID;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryHandler
        implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("replace.constructor.with.factory.method.title");
  private Project myProject;

  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        LocalizeValue message =
          RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.errorWrongCaretPositionConstructor());
        CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
        return;
      }

      if (element instanceof PsiReferenceExpression) {
        final PsiElement psiElement = ((PsiReferenceExpression)element).resolve();
        if (psiElement instanceof PsiMethod && ((PsiMethod) psiElement).isConstructor()) {
          invoke(project, new PsiElement[] { psiElement }, dataContext);
          return;
        }
      }
      else if (element instanceof PsiConstructorCall) {
        final PsiConstructorCall constructorCall = (PsiConstructorCall)element;
        final PsiMethod method = constructorCall.resolveConstructor();
        if (method != null) {
          invoke(project, new PsiElement[] { method }, dataContext);
          return;
        }
        // handle default constructor
        if (element instanceof PsiNewExpression) {
          final PsiJavaCodeReferenceElement classReference = ((PsiNewExpression)element).getClassReference();
          if (classReference != null) {
            final PsiElement classElement = classReference.resolve();
            if (classElement instanceof PsiClass) {
              invoke(project, new PsiElement[] { classElement }, dataContext);
              return;
            }
          }
        }
      }

      if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)
          && ((PsiClass) element).getConstructors().length == 0) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      if (element instanceof PsiMethod && ((PsiMethod) element).isConstructor()) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    myProject = project;
    Editor editor = dataContext.getData(Editor.KEY);
    if (elements[0] instanceof PsiMethod method) {
      invoke(method, editor);
    }
    else if (elements[0] instanceof PsiClass psiClass) {
      invoke(psiClass, editor);
    }
  }

  private void invoke(PsiClass aClass, Editor editor) {
    String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) {
      showJspOrLocalClassMessage(editor);
      return;
    }
    if (!checkAbstractClassOrInterfaceMessage(aClass, editor)) return;
    final PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length > 0) {
      String message =
        RefactoringLocalize.classDoesNotHaveImplicitDefaultConstructor(aClass.getQualifiedName()).get();
      CommonRefactoringUtil.showErrorHint(myProject, editor, message, REFACTORING_NAME, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
      return;
    }
    final int answer = Messages.showYesNoDialog(
      myProject,
      RefactoringLocalize.wouldYouLikeToReplaceDefaultConstructorOf0WithFactoryMethod(aClass.getQualifiedName()).get(),
      REFACTORING_NAME,
      UIUtil.getQuestionIcon()
    );
    if (answer != 0) return;
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, aClass)) return;
    new ReplaceConstructorWithFactoryDialog(myProject, null, aClass).show();
  }

  private void showJspOrLocalClassMessage(Editor editor) {
    LocalizeValue message =
      RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.refactoringIsNotSupportedForLocalAndJspClasses());
    CommonRefactoringUtil.showErrorHint(myProject, editor, message.get(), REFACTORING_NAME, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
  }
  private boolean checkAbstractClassOrInterfaceMessage(PsiClass aClass, Editor editor) {
    if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) return true;
    LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
      aClass.isInterface()
        ? RefactoringLocalize.classIsInterface(aClass.getQualifiedName())
        : RefactoringLocalize.classIsAbstract(aClass.getQualifiedName())
    );
    CommonRefactoringUtil.showErrorHint(myProject, editor, message.get(), REFACTORING_NAME, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
    return false;
  }

  private void invoke(final PsiMethod method, Editor editor) {
    if (!method.isConstructor()) {
      LocalizeValue message =
        RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.methodIsNotAConstructor());
      CommonRefactoringUtil.showErrorHint(myProject, editor, message.get(), REFACTORING_NAME, HelpID.REPLACE_CONSTRUCTOR_WITH_FACTORY);
      return;
    }

    PsiClass aClass = method.getContainingClass();
    if (aClass == null || aClass.getQualifiedName() == null) {
      showJspOrLocalClassMessage(editor);
      return;
    }

    if (!checkAbstractClassOrInterfaceMessage(aClass, editor)) return;

    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, method)) return;
    new ReplaceConstructorWithFactoryDialog(myProject, method, method.getContainingClass()).show();
  }
}
