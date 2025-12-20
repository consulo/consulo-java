/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.changeSignature;

import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.lang.java.JavaRefactoringSupportProvider;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.changeClassSignature.ChangeClassSignatureDialog;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.java.impl.codeInsight.JavaTargetElementUtilEx;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class JavaChangeSignatureHandler implements ChangeSignatureHandler {
  @Override
  @RequiredUIAccess
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = findTargetMember(file, editor);
    if (element == null) {
      element = dataContext.getData(PsiElement.KEY);
    }
    invokeOnElement(project, editor, element);
  }

  @RequiredUIAccess
  private static void invokeOnElement(Project project, Editor editor, PsiElement element) {
    if (element instanceof PsiMethod method && method.getNameIdentifier() != null) {
      invoke(method, project, editor);
    } else if (element instanceof PsiClass psiClass) {
      invoke(psiClass, editor);
    } else {
      LocalizeValue message =
          RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.errorWrongCaretPositionMethodOrClassName());
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME.get(), HelpID.CHANGE_SIGNATURE);
    }
  }

  @Override
  @RequiredUIAccess
  public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, @Nullable DataContext dataContext) {
    if (elements.length != 1) {
      return;
    }
    Editor editor = dataContext != null ? dataContext.getData(Editor.KEY) : null;
    invokeOnElement(project, editor, elements[0]);
  }

  @Nullable
  @Override
  public String getTargetNotFoundMessage() {
    return RefactoringLocalize.errorWrongCaretPositionMethodOrClassName().get();
  }

  @RequiredUIAccess
  private static void invoke(PsiMethod method, Project project, @Nullable Editor editor) {
    PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringLocalize.toRefactor().get());
    if (newMethod == null) {
      return;
    }

    if (!newMethod.equals(method)) {
      ChangeSignatureUtil.invokeChangeSignatureOn(newMethod, project);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) {
      return;
    }

    PsiClass containingClass = method.getContainingClass();
    PsiReferenceExpression refExpr = editor != null ? JavaTargetElementUtilEx.findReferenceExpression(editor) : null;
    boolean allowDelegation = containingClass != null && !containingClass.isInterface();
    DialogWrapper dialog = new JavaChangeSignatureDialog(project, method, allowDelegation, refExpr);
    dialog.show();
  }

  @RequiredUIAccess
  private static void invoke(PsiClass aClass, Editor editor) {
    PsiTypeParameterList typeParameterList = aClass.getTypeParameterList();
    Project project = aClass.getProject();
    if (typeParameterList == null) {
      LocalizeValue message =
        RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.changeclasssignatureNoTypeParameters());
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME.get(), HelpID.CHANGE_CLASS_SIGNATURE);
      return;
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) {
      return;
    }

    ChangeClassSignatureDialog dialog = new ChangeClassSignatureDialog(aClass, true);
    dialog.show();
  }

  @Override
  @Nullable
  @RequiredUIAccess
  public PsiElement findTargetMember(PsiFile file, Editor editor) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    return findTargetMember(element);
  }

  @Override
  @RequiredReadAction
  public PsiElement findTargetMember(PsiElement element) {
    PsiElement target = findTargetImpl(element);
    return JavaRefactoringSupportProvider.isDisableRefactoringForLightElement(target) ? null : target;
  }

  @RequiredReadAction
  private PsiElement findTargetImpl(PsiElement element) {
    if (PsiTreeUtil.getParentOfType(element, PsiParameterList.class) != null) {
      return PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    }

    PsiTypeParameterList typeParameterList = PsiTreeUtil.getParentOfType(element, PsiTypeParameterList.class);
    if (typeParameterList != null) {
      return PsiTreeUtil.getParentOfType(typeParameterList, PsiMember.class);
    }

    PsiElement elementParent = element.getParent();
    if (elementParent instanceof PsiMethod method && method.getNameIdentifier() == element) {
      PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && containingClass.isAnnotationType()) {
        return null;
      }
      return elementParent;
    }
    if (elementParent instanceof PsiClass psiClass && psiClass.getNameIdentifier() == element) {
      return psiClass.isAnnotationType() ? null : elementParent;
    }

    PsiCallExpression expression = PsiTreeUtil.getParentOfType(element, PsiCallExpression.class);
    if (expression != null) {
      PsiExpression qualifierExpression;
      if (expression instanceof PsiMethodCallExpression methodCall) {
        qualifierExpression = methodCall.getMethodExpression().getQualifierExpression();
      } else if (expression instanceof PsiNewExpression newExpression) {
        qualifierExpression = newExpression.getQualifier();
      } else {
        qualifierExpression = null;
      }
      if (PsiTreeUtil.isAncestor(qualifierExpression, element, false)) {
        PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(qualifierExpression, PsiExpressionList.class);
        if (expressionList != null) {
          PsiElement parent = expressionList.getParent();
          if (parent instanceof PsiCallExpression call) {
            return call.resolveMethod();
          }
        }
      } else {
        return expression.resolveMethod();
      }
    }

    PsiReferenceParameterList referenceParameterList = PsiTreeUtil.getParentOfType(element, PsiReferenceParameterList.class);
    if (referenceParameterList != null) {
      PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getParentOfType(referenceParameterList, PsiJavaCodeReferenceElement.class);
      if (referenceElement != null) {
        PsiElement resolved = referenceElement.resolve();
        if (resolved instanceof PsiClass) {
          return resolved;
        } else if (resolved instanceof PsiMethod) {
          return resolved;
        }
      }
    }
    return null;
  }
}
