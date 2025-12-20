// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.refactoring.util;

import com.intellij.java.language.psi.*;
import consulo.document.Document;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.DumbService;
import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

/**
 * Common utils shared between refactorings module and 'java.impl' module.
 */
public class CommonJavaRefactoringUtil {
  @Contract(value = "null -> null", pure = true)
  public static PsiExpression unparenthesizeExpression(PsiExpression expression) {
    while (expression instanceof PsiParenthesizedExpression) {
      PsiExpression innerExpression = ((PsiParenthesizedExpression)expression).getExpression();
      if (innerExpression == null) return expression;
      expression = innerExpression;
    }
    return expression;
  }

  public static List<PsiExpression> collectExpressions(PsiFile file,
                                                       Document document,
                                                       int offset,
                                                       boolean acceptVoid) {
    CharSequence text = document.getCharsSequence();
    int correctedOffset = offset;
    int textLength = document.getTextLength();
    if (offset >= textLength) {
      correctedOffset = textLength - 1;
    }
    else if (!Character.isJavaIdentifierPart(text.charAt(offset))) {
      correctedOffset--;
    }
    if (correctedOffset < 0) {
      correctedOffset = offset;
    }
    else if (!Character.isJavaIdentifierPart(text.charAt(correctedOffset))) {
      if (text.charAt(correctedOffset) == ';') {//initially caret on the end of line
        correctedOffset--;
      }
      if (correctedOffset < 0 || text.charAt(correctedOffset) != ')' && text.charAt(correctedOffset) != '.' && text.charAt(correctedOffset) != '}') {
        correctedOffset = offset;
      }
    }
    PsiElement elementAtCaret = file.findElementAt(correctedOffset);
    List<PsiExpression> expressions = new ArrayList<>();
    PsiExpression expression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
    while (expression != null) {
      if (!expressions.contains(expression) && !(expression instanceof PsiParenthesizedExpression) && !(expression instanceof PsiSuperExpression) &&
        (acceptVoid || !PsiTypes.voidType().equals(DumbService.getInstance(file.getProject())
                                                              .computeWithAlternativeResolveEnabled(expression::getType)))) {
        if (isExtractable(expression)) {
          expressions.add(expression);
        }
      }
      expression = PsiTreeUtil.getParentOfType(expression, PsiExpression.class);
    }
    return expressions;
  }

  public static boolean isExtractable(PsiExpression expression) {
    if (expression instanceof PsiMethodReferenceExpression) {
      return true;
    }
    else if (!(expression instanceof PsiAssignmentExpression)) {
      if (!(expression instanceof PsiReferenceExpression)) {
        return true;
      }
      else {
        if (!(expression.getParent() instanceof PsiMethodCallExpression)) {
          PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
          if (!(resolve instanceof PsiClass) && !(resolve instanceof PsiPackage)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
