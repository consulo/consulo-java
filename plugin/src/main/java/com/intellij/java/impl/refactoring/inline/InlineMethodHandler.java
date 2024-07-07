
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
package com.intellij.java.impl.refactoring.inline;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.util.InlineUtil;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.virtualFileSystem.ReadonlyStatusHandler;
import consulo.virtualFileSystem.VirtualFile;

@ExtensionImpl
public class InlineMethodHandler extends JavaInlineActionHandler {
  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.method.title");

  public boolean canInlineElement(PsiElement element) {
    return element instanceof PsiMethod && element.getNavigationElement() instanceof PsiMethod && element.getLanguage() == JavaLanguage.INSTANCE;
  }

  public void inlineElement(final Project project, Editor editor, PsiElement element) {
    PsiMethod method = (PsiMethod) element.getNavigationElement();
    final PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) {
      LocalizeValue message = method.hasModifierProperty(PsiModifier.ABSTRACT)
        ? RefactoringLocalize.refactoringCannotBeAppliedToAbstractMethods(REFACTORING_NAME)
        : RefactoringLocalize.refactoringCannotBeAppliedNoSourcesAttached(REFACTORING_NAME);
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_METHOD);
      return;
    }

    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    if (reference != null) {
      final PsiElement refElement = reference.getElement();
      if (refElement != null && !isEnabledForLanguage(refElement.getLanguage())) {
        LocalizeValue message =
          RefactoringLocalize.refactoringIsNotSupportedForLanguage("Inline of Java method", refElement.getLanguage().getDisplayName());
        CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_METHOD);
        return;
      }
    }
    boolean allowInlineThisOnly = false;
    if (InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method)) {
      if (reference != null && InlineUtil.getTailCallType(reference) != InlineUtil.TailCallType.None) {
        allowInlineThisOnly = true;
      } else {
        LocalizeValue message = RefactoringLocalize.refactoringIsNotSupportedWhenReturnStatementInterruptsTheExecutionFlow(REFACTORING_NAME);
        CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_METHOD);
        return;
      }
    }

    if (reference == null && checkRecursive(method)) {
      LocalizeValue message = RefactoringLocalize.refactoringIsNotSupportedForRecursiveMethods(REFACTORING_NAME);
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_METHOD);
      return;
    }

    if (reference instanceof PsiMethodReferenceExpression) {
      CommonRefactoringUtil.showErrorHint(project, editor, REFACTORING_NAME + " cannot be applied to method references", REFACTORING_NAME, HelpID.INLINE_METHOD);
      return;
    }

    if (reference != null) {
      final String errorMessage = InlineMethodProcessor.checkCalledInSuperOrThisExpr(methodBody, reference.getElement());
      if (errorMessage != null) {
        CommonRefactoringUtil.showErrorHint(project, editor, errorMessage, REFACTORING_NAME, HelpID.INLINE_METHOD);
        return;
      }
    }

    if (method.isConstructor()) {
      if (method.isVarArgs()) {
        LocalizeValue message = RefactoringLocalize.refactoringCannotBeAppliedToVarargConstructors(REFACTORING_NAME);
        CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_CONSTRUCTOR);
        return;
      }
      final boolean chainingConstructor = isChainingConstructor(method);
      if (!chainingConstructor) {
        if (!isThisReference(reference)) {
          LocalizeValue message = RefactoringLocalize.refactoringCannotBeAppliedToInlineNonChainingConstructors(REFACTORING_NAME);
          CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.INLINE_CONSTRUCTOR);
          return;
        }
        allowInlineThisOnly = true;
      }
      if (reference != null) {
        final PsiElement refElement = reference.getElement();
        PsiCall constructorCall = refElement instanceof PsiJavaCodeReferenceElement ? RefactoringUtil.getEnclosingConstructorCall((PsiJavaCodeReferenceElement) refElement) : null;
        if (constructorCall == null || !method.equals(constructorCall.resolveMethod())) reference = null;
      }
    } else {
      if (reference != null && !method.getManager().areElementsEquivalent(method, reference.resolve())) {
        reference = null;
      }
    }

    final boolean invokedOnReference = reference != null;
    if (!invokedOnReference) {
      final VirtualFile vFile = method.getContainingFile().getVirtualFile();
      ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(vFile);
    }
    PsiJavaCodeReferenceElement refElement = reference != null ? (PsiJavaCodeReferenceElement) reference.getElement() : null;
    InlineMethodDialog dialog = new InlineMethodDialog(project, method, refElement, editor, allowInlineThisOnly);
    dialog.show();
  }

  public static boolean isChainingConstructor(PsiMethod constructor) {
    PsiCodeBlock body = constructor.getBody();
    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
        PsiExpression expression = ((PsiExpressionStatement) statements[0]).getExpression();
        if (expression instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodExpr = ((PsiMethodCallExpression) expression).getMethodExpression();
          if ("this".equals(methodExpr.getReferenceName())) {
            PsiElement resolved = methodExpr.resolve();
            return resolved instanceof PsiMethod && ((PsiMethod) resolved).isConstructor(); //delegated via "this" call
          }
        }
      }
    }
    return false;
  }

  public static boolean checkRecursive(PsiMethod method) {
    return checkCalls(method.getBody(), method);
  }

  private static boolean checkCalls(PsiElement scope, PsiMethod method) {
    if (scope instanceof PsiMethodCallExpression) {
      PsiMethod refMethod = (PsiMethod) ((PsiMethodCallExpression) scope).getMethodExpression().resolve();
      if (method.equals(refMethod)) return true;
    }

    for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (checkCalls(child, method)) return true;
    }

    return false;
  }

  public static boolean isThisReference(PsiReference reference) {
    if (reference != null) {
      final PsiElement referenceElement = reference.getElement();
      if (referenceElement instanceof PsiJavaCodeReferenceElement &&
          referenceElement.getParent() instanceof PsiMethodCallExpression &&
          "this".equals(((PsiJavaCodeReferenceElement) referenceElement).getReferenceName())) {
        return true;
      }
    }
    return false;
  }
}