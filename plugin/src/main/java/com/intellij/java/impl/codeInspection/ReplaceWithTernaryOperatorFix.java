/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.codeInspection;

import com.intellij.java.impl.refactoring.util.LambdaRefactoringUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiTypesUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.fileEditor.SelectInEditorManager;
import consulo.java.analysis.impl.codeInsight.JavaInspectionsBundle;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;

/**
 * @author Danila Ponomarenko
 */
public class ReplaceWithTernaryOperatorFix implements LocalQuickFix {
  private final String myText;

  @Override
  @Nonnull
  public String getName() {
    return InspectionLocalize.inspectionReplaceTernaryQuickfix(myText).get();
  }

  public ReplaceWithTernaryOperatorFix(@Nonnull PsiExpression expressionToAssert) {
    myText = ParenthesesUtils.getText(expressionToAssert, ParenthesesUtils.BINARY_AND_PRECEDENCE);
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return InspectionLocalize.inspectionSurroundIfFamily().get();
  }

  @Override
  @RequiredWriteAction
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    while (true) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiReferenceExpression || parent instanceof PsiMethodCallExpression) {
        element = parent;
      } else {
        break;
      }
    }
    if (!(element instanceof PsiExpression)) {
      return;
    }
    final PsiExpression expression = (PsiExpression) element;

    final PsiFile file = expression.getContainingFile();
    PsiConditionalExpression conditionalExpression = replaceWithConditionalExpression(project, myText + "!=null", expression, suggestDefaultValue(expression));

    selectElseBranch(file, conditionalExpression);
  }

  @RequiredReadAction
  static void selectElseBranch(PsiFile file, PsiConditionalExpression conditionalExpression) {
    PsiExpression elseExpression = conditionalExpression.getElseExpression();
    if (elseExpression != null) {
      ((Navigatable) elseExpression).navigate(true);
      SelectInEditorManager.getInstance(file.getProject()).selectInEditor(
        file.getVirtualFile(),
        elseExpression.getTextRange().getStartOffset(),
        elseExpression.getTextRange().getEndOffset(),
        false,
        true
      );
    }
  }

  @Nonnull
  @RequiredReadAction
  private static PsiConditionalExpression replaceWithConditionalExpression(
    @Nonnull Project project,
    @Nonnull String condition,
    @Nonnull PsiExpression expression,
    @Nonnull String defaultValue
  ) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

    final PsiElement parent = expression.getParent();
    final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression) factory.createExpressionFromText(
      condition + " ? " + expression.getText() + " : " + defaultValue,
      parent
    );

    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    return (PsiConditionalExpression) expression.replace(codeStyleManager.reformat(conditionalExpression));
  }

  @RequiredReadAction
  public static boolean isAvailable(@Nonnull PsiExpression qualifier, @Nonnull PsiExpression expression) {
    return qualifier.isValid() && qualifier.getText() != null && !(expression.getParent() instanceof PsiExpressionStatement)
      && !PsiUtil.isAccessedForWriting(expression);

  }

  private static String suggestDefaultValue(@Nonnull PsiExpression expression) {
    PsiType type = expression.getType();
    return PsiTypesUtil.getDefaultValueOfType(type);
  }

  public static class ReplaceMethodRefWithTernaryOperatorFix implements LocalQuickFix {
    @Nonnull
    @Override
    public String getFamilyName() {
      return JavaInspectionsBundle.message("inspection.replace.methodref.ternary.quickfix");
    }

    @Override
    @RequiredWriteAction
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
      PsiMethodReferenceExpression element = ObjectUtil.tryCast(descriptor.getPsiElement(), PsiMethodReferenceExpression.class);
      if (element == null) {
        return;
      }
      PsiLambdaExpression lambda = LambdaRefactoringUtil.convertMethodReferenceToLambda(element, false, true);
      if (lambda == null) {
        return;
      }
      PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      if (expression == null) {
        return;
      }
      PsiParameter parameter = ArrayUtil.getFirstElement(lambda.getParameterList().getParameters());
      if (parameter == null) {
        return;
      }
      String text = parameter.getName();
      final PsiFile file = expression.getContainingFile();
      PsiConditionalExpression conditionalExpression = replaceWithConditionalExpression(project, text + "!=null", expression, suggestDefaultValue(expression));

      selectElseBranch(file, conditionalExpression);
    }
  }
}
