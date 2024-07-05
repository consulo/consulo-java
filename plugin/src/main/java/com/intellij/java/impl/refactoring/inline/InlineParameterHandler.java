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

import com.intellij.java.analysis.impl.psi.controlFlow.DefUseUtil;
import com.intellij.java.impl.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.util.InlineUtil;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Result;
import consulo.codeEditor.Editor;
import consulo.language.editor.PsiEquivalenceUtil;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.refactoring.util.RefactoringMessageDialog;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author yole
 */
@ExtensionImpl
public class InlineParameterHandler extends JavaInlineActionHandler {
  private static final Logger LOG = Logger.getInstance(InlineParameterHandler.class);
  public static final String REFACTORING_NAME = RefactoringBundle.message("inline.parameter.refactoring");

  @RequiredReadAction
  public boolean canInlineElement(PsiElement element) {
    if (element instanceof PsiParameter) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiParameterList &&
          parent.getParent() instanceof PsiMethod &&
          element.getLanguage() == JavaLanguage.INSTANCE) {
        return true;
      }
    }
    return false;
  }

  @RequiredReadAction
  @RequiredUIAccess
  public void inlineElement(final Project project, final Editor editor, final PsiElement psiElement) {
    final PsiParameter psiParameter = (PsiParameter) psiElement;
    final PsiParameterList parameterList = (PsiParameterList) psiParameter.getParent();
    if (!(parameterList.getParent() instanceof PsiMethod)) {
      return;
    }
    final int index = parameterList.getParameterIndex(psiParameter);
    final PsiMethod method = (PsiMethod) parameterList.getParent();

    String errorMessage = getCannotInlineMessage(psiParameter, method);
    if (errorMessage != null) {
      CommonRefactoringUtil.showErrorHint(project, editor, errorMessage, RefactoringLocalize.inlineParameterRefactoring().get(), null);
      return;
    }

    final Ref<PsiExpression> refInitializer = new Ref<>();
    final Ref<PsiExpression> refConstantInitializer = new Ref<>();
    final Ref<PsiCallExpression> refMethodCall = new Ref<>();
    final List<PsiReference> occurrences = Collections.synchronizedList(new ArrayList<PsiReference>());
    final Collection<PsiFile> containingFiles = Collections.synchronizedSet(new HashSet<PsiFile>());
    containingFiles.add(psiParameter.getContainingFile());
    boolean result = ReferencesSearch.search(method).forEach(psiReference -> {
      PsiElement element = psiReference.getElement();
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiCallExpression methodCall) {
        occurrences.add(psiReference);
        containingFiles.add(element.getContainingFile());
        final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
        if (expressions.length <= index) return false;
        PsiExpression argument = expressions[index];
        if (!refInitializer.isNull()) {
          return argument != null
            && PsiEquivalenceUtil.areElementsEquivalent(refInitializer.get(), argument)
            && PsiEquivalenceUtil.areElementsEquivalent(refMethodCall.get(), methodCall);
        }
        if (InlineToAnonymousConstructorProcessor.isConstant(argument) || getReferencedFinalField(argument) != null) {
          if (refConstantInitializer.isNull()) {
            refConstantInitializer.set(argument);
          }
          else if (!isSameConstant(argument, refConstantInitializer.get())) {
            return false;
          }
        } else if (!isRecursiveReferencedParameter(argument, psiParameter)) {
          if (!refConstantInitializer.isNull()) return false;
          refInitializer.set(argument);
          refMethodCall.set(methodCall);
        }
      }
      return true;
    });
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement refExpr = psiElement.getContainingFile().findElementAt(offset);
    final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(refExpr, PsiCodeBlock.class);
    if (codeBlock != null) {
      final PsiElement[] defs = DefUseUtil.getDefs(codeBlock, psiParameter, refExpr);
      if (defs.length == 1) {
        final PsiElement def = defs[0];
        if (def instanceof PsiReferenceExpression referenceExpression && PsiUtil.isOnAssignmentLeftHand(referenceExpression)) {
          final PsiExpression rExpr = ((PsiAssignmentExpression)def.getParent()).getRExpression();
          if (rExpr != null) {
            final PsiElement[] refs = DefUseUtil.getRefs(codeBlock, psiParameter, refExpr);

            if (InlineLocalHandler.checkRefsInAugmentedAssignmentOrUnaryModified(refs) == null) {
              new WriteCommandAction(project) {
                @Override
                protected void run(Result result) throws Throwable {
                  for (final PsiElement ref : refs) {
                    InlineUtil.inlineVariable(psiParameter, rExpr, (PsiJavaCodeReferenceElement)ref);
                  }
                  def.getParent().delete();
                }
              }.execute();
              return;
            }
          }
        }
      }
    }
    if (occurrences.isEmpty()) {
      CommonRefactoringUtil
        .showErrorHint(project, editor, "Method has no usages", RefactoringLocalize.inlineParameterRefactoring().get(), null);
      return;
    }
    if (!result) {
      CommonRefactoringUtil.showErrorHint(
        project,
        editor,
        "Cannot find constant initializer for parameter",
        RefactoringLocalize.inlineParameterRefactoring().get(),
        null
      );
      return;
    }
    if (!refInitializer.isNull()) {
      if (project.getApplication().isUnitTestMode()) {
        final InlineParameterExpressionProcessor processor =
          new InlineParameterExpressionProcessor(
            refMethodCall.get(),
            method,
            psiParameter,
            refInitializer.get(),
            method.getProject().getUserData(InlineParameterExpressionProcessor.CREATE_LOCAL_FOR_TESTS)
          );
        processor.run();
      }
      else {
        final boolean createLocal = ReferencesSearch.search(psiParameter).findAll().size() > 1;
        InlineParameterDialog dlg = new InlineParameterDialog(refMethodCall.get(), method, psiParameter, refInitializer.get(), createLocal);
        dlg.show();
      }
      return;
    }
    if (refConstantInitializer.isNull()) {
      CommonRefactoringUtil.showErrorHint(
        project,
        editor,
        "Cannot find constant initializer for parameter",
        RefactoringLocalize.inlineParameterRefactoring().get(),
        null
      );
      return;
    }

    final Ref<Boolean> isNotConstantAccessible = new Ref<>();
    final PsiExpression constantExpression = refConstantInitializer.get();
    constantExpression.accept(new JavaRecursiveElementVisitor(){
      @Override
      @RequiredReadAction
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiMember member && !PsiUtil.isAccessible(member, method, null)) {
          isNotConstantAccessible.set(Boolean.TRUE);
        }
      }
    });
    if (!isNotConstantAccessible.isNull() && isNotConstantAccessible.get()) {
      CommonRefactoringUtil.showErrorHint(
        project,
        editor,
        "Constant initializer is not accessible in method body",
        RefactoringLocalize.inlineParameterRefactoring().get(),
        null
      );
      return;
    }

    for (PsiReference psiReference : ReferencesSearch.search(psiParameter)) {
      final PsiElement element = psiReference.getElement();
      if (element instanceof PsiExpression expression && PsiUtil.isAccessedForWriting(expression)) {
        CommonRefactoringUtil.showErrorHint(
          project,
          editor,
          "Inline parameter which has write usages is not supported",
          RefactoringLocalize.inlineParameterRefactoring().get(),
          null
        );
        return;
      }
    }

    if (!project.getApplication().isUnitTestMode()) {
      LocalizeValue occurencesString = RefactoringLocalize.occurencesString(occurrences.size());
      String question = RefactoringLocalize.inlineParameterConfirmation(psiParameter.getName(), constantExpression.getText()).get()
        + " " + occurencesString;
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(
        REFACTORING_NAME,
        question,
        HelpID.INLINE_VARIABLE,
        "OptionPane.questionIcon",
        true,
        project
      );
      dialog.show();
      if (!dialog.isOK()){
        return;
      }
    }

    SameParameterValueInspection.InlineParameterValueFix.inlineSameParameterValue(method, psiParameter, constantExpression);
  }

  @Nullable
  @RequiredReadAction
  private static PsiField getReferencedFinalField(final PsiExpression argument) {
    if (argument instanceof PsiReferenceExpression referenceExpression) {
      final PsiElement element = referenceExpression.resolve();
      if (element instanceof PsiField field) {
        final PsiModifierList modifierList = field.getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.FINAL)) {
          return field;
        }
      }
    }
    return null;
  }

  @RequiredReadAction
  private static boolean isRecursiveReferencedParameter(final PsiExpression argument, final PsiParameter param) {
    if (argument instanceof PsiReferenceExpression referenceExpression) {
      final PsiElement element = referenceExpression.resolve();
      if (element instanceof PsiParameter) {
        return element.equals(param);
      }
    }
    return false;
  }

  @RequiredReadAction
  private static boolean isSameConstant(final PsiExpression expr1, final PsiExpression expr2) {
    boolean expr1Null = InlineToAnonymousConstructorProcessor.ourNullPattern.accepts(expr1);
    boolean expr2Null = InlineToAnonymousConstructorProcessor.ourNullPattern.accepts(expr2);
    if (expr1Null || expr2Null) {
      return expr1Null && expr2Null;
    }
    final PsiField field1 = getReferencedFinalField(expr1);
    final PsiField field2 = getReferencedFinalField(expr2);
    if (field1 != null && field1 == field2) {
      return true;
    }
    Object value1 = JavaPsiFacade.getInstance(expr1.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr1);
    Object value2 = JavaPsiFacade.getInstance(expr2.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr2);
    return value1 != null && value2 != null && value1.equals(value2);
  }

  @Nullable
  private static String getCannotInlineMessage(final PsiParameter psiParameter, final PsiMethod method) {
    if (psiParameter.isVarArgs()) {
      return RefactoringLocalize.inlineParameterErrorVarargs().get();
    }
    if (method.findSuperMethods().length > 0 ||
        OverridingMethodsSearch.search(method, true).toArray(PsiMethod.EMPTY_ARRAY).length > 0) {
      return RefactoringLocalize.inlineParameterErrorHierarchy().get();
    }
    return null;
  }
}
