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

package com.intellij.java.analysis.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.ast.IElementType;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ChangeToAppendFix implements SyntheticIntentionAction {

  private final IElementType myTokenType;
  private final PsiType myLhsType;
  private final PsiAssignmentExpression myAssignmentExpression;

  public ChangeToAppendFix(IElementType eqOpSign, PsiType lType, PsiAssignmentExpression assignmentExpression) {
    myTokenType = eqOpSign;
    myLhsType = lType;
    myAssignmentExpression = assignmentExpression;
  }

  @Nonnull
  @Override
  public String getText() {
    return JavaQuickFixBundle.message("change.to.append.text",
                                  buildAppendExpression(myAssignmentExpression.getRExpression(),
                                                        myLhsType.equalsToText("java.lang.Appendable"),
                                                        new StringBuilder(myAssignmentExpression.getLExpression().getText())));
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return JavaQuickFixBundle.message("change.to.append.family");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return JavaTokenType.PLUSEQ == myTokenType &&
           myAssignmentExpression.isValid() &&
           PsiManager.getInstance(project).isInProject(myAssignmentExpression) &&
           (myLhsType.equalsToText(JavaClassNames.JAVA_LANG_STRING_BUILDER) ||
            myLhsType.equalsToText(JavaClassNames.JAVA_LANG_STRING_BUFFER) ||
            myLhsType.equalsToText("java.lang.Appendable"));
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    final PsiExpression appendExpression =
      buildAppendExpression(myAssignmentExpression.getLExpression(), myAssignmentExpression.getRExpression());
    if (appendExpression == null) return;
    myAssignmentExpression.replace(appendExpression);
  }

  @Nullable
  public static PsiExpression buildAppendExpression(PsiExpression appendable, PsiExpression concatenation) {
    if (concatenation == null) return null;
    final PsiType type = appendable.getType();
    if (type == null) return null;
    final StringBuilder result =
      buildAppendExpression(concatenation, type.equalsToText("java.lang.Appendable"), new StringBuilder(appendable.getText()));
    if (result == null) return null;
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(appendable.getProject());
    return factory.createExpressionFromText(result.toString(), appendable);
  }

  @Nullable
  private static StringBuilder buildAppendExpression(@Nullable PsiExpression concatenation, boolean useStringValueOf, @NonNls StringBuilder out)
    throws IncorrectOperationException {
    final PsiType type = concatenation == null ? null : concatenation.getType();
    if (type == null) {
      return null;
    }
    if (concatenation instanceof PsiPolyadicExpression && type.equalsToText(JavaClassNames.JAVA_LANG_STRING)) {
      PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)concatenation;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      boolean isConstant = true;
      boolean isString = false;
      final StringBuilder builder = new StringBuilder();
      for (PsiExpression operand : operands) {
        if (isConstant && PsiUtil.isConstantExpression(operand)) {
          if (builder.length() != 0) {
            builder.append('+');
          }
          final PsiType operandType = operand.getType();
          if (operandType != null && operandType.equalsToText(JavaClassNames.JAVA_LANG_STRING)) {
            isString = true;
          }
          builder.append(operand.getText());
        }
        else {
          isConstant = false;
          if (builder.length() != 0) {
            append(builder, useStringValueOf && !isString, out);
            builder.setLength(0);
          }
          buildAppendExpression(operand, useStringValueOf, out);
        }
      }
      if (builder.length() != 0) {
        append(builder, false, out);
      }
    }
    else if (concatenation instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)concatenation;
      final PsiExpression expression = parenthesizedExpression.getExpression();
      if (expression != null) {
        return buildAppendExpression(expression, useStringValueOf, out);
      }
    }
    else {
      append(concatenation.getText(), useStringValueOf && !type.equalsToText(JavaClassNames.JAVA_LANG_STRING), out);
    }
    return out;
  }

  private static void append(CharSequence text, boolean useStringValueOf, StringBuilder out) {
    out.append(".append(");
    if (useStringValueOf) {
      out.append("String.valueOf(").append(text).append(')');
    }
    else {
      out.append(text);
    }
    out.append(')');
  }
}
