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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PsiConcatenationUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ConcatenationToMessageFormatAction", categories = {"Java", "I18N"}, fileExtensions = "java")
public class ConcatenationToMessageFormatAction implements IntentionAction {
  @Override
  @Nonnull
  public LocalizeValue getText() {
    return CodeInsightLocalize.intentionReplaceConcatenationWithFormattedOutputText();
  }

  @Override
  @RequiredReadAction
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    PsiElement element = findElementAtCaret(editor, file);
    PsiPolyadicExpression concatenation = getEnclosingLiteralConcatenation(element);
    if (concatenation == null) return;
    StringBuilder formatString = new StringBuilder();
    List<PsiExpression> args = new ArrayList<>();
    buildMessageFormatString(concatenation, formatString, args);

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiMethodCallExpression call = (PsiMethodCallExpression)
      factory.createExpressionFromText("java.text.MessageFormat.format()", concatenation);
    PsiExpressionList argumentList = call.getArgumentList();
    PsiExpression formatArgument = factory.createExpressionFromText("\"" + formatString.toString() + "\"", null);
    argumentList.add(formatArgument);
    if (PsiUtil.isLanguageLevel5OrHigher(file)) {
      for (PsiExpression arg : args) {
        argumentList.add(arg);
      }
    }
    else {
      PsiNewExpression arrayArg = (PsiNewExpression)factory.createExpressionFromText("new java.lang.Object[]{}", null);
      PsiArrayInitializerExpression arrayInitializer = arrayArg.getArrayInitializer();
      assert arrayInitializer != null;
      for (PsiExpression arg : args) {
        arrayInitializer.add(arg);
      }
      argumentList.add(arrayArg);
    }
    call = (PsiMethodCallExpression) JavaCodeStyleManager.getInstance(project).shortenClassReferences(call);
    call = (PsiMethodCallExpression) CodeStyleManager.getInstance(element.getManager().getProject()).reformat(call);
    concatenation.replace(call);
  }

  public static void buildMessageFormatString(
    PsiExpression expression,
    StringBuilder formatString,
    List<PsiExpression> args
  ) throws IncorrectOperationException {
    PsiConcatenationUtil.buildFormatString(expression, formatString, args, false);
  }

  private static void appendArgument(
    List<PsiExpression> args,
    PsiExpression argument,
    StringBuilder formatString
  ) throws IncorrectOperationException {
    formatString.append("{").append(args.size()).append("}");
    args.add(getBoxedArgument(argument));
  }

  private static PsiExpression getBoxedArgument(PsiExpression arg) throws IncorrectOperationException {
    arg = PsiUtil.deparenthesizeExpression(arg);
    assert arg != null;
    if (PsiUtil.isLanguageLevel5OrHigher(arg)) {
      return arg;
    }
    PsiType type = arg.getType();
    if (!(type instanceof PsiPrimitiveType) || type.equals(PsiType.NULL)) {
      return arg;
    }
    PsiPrimitiveType primitiveType = (PsiPrimitiveType)type;
    String boxedQName = primitiveType.getBoxedTypeName();
    if (boxedQName == null) {
      return arg;
    }
    GlobalSearchScope resolveScope = arg.getResolveScope();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(arg.getProject());
    PsiJavaCodeReferenceElement ref = factory.createReferenceElementByFQClassName(boxedQName, resolveScope);
    PsiNewExpression newExpr = (PsiNewExpression)factory.createExpressionFromText("new A(b)", null);
    PsiElement classRef = newExpr.getClassReference();
    assert classRef != null;
    classRef.replace(ref);
    PsiExpressionList argumentList = newExpr.getArgumentList();
    assert argumentList != null;
    argumentList.getExpressions()[0].replace(arg);
    return newExpr;
  }

  @Override
  @RequiredReadAction
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (PsiUtil.getLanguageLevel(file).compareTo(LanguageLevel.JDK_1_4) < 0) return false;
    PsiElement element = findElementAtCaret(editor, file);
    PsiPolyadicExpression binaryExpression = getEnclosingLiteralConcatenation(element);
    return binaryExpression != null && !AnnotationUtil.isInsideAnnotation(binaryExpression);
  }

  @Nullable
  @RequiredReadAction
  private static PsiElement findElementAtCaret(Editor editor, PsiFile file) {
    return file.findElementAt(editor.getCaretModel().getOffset());
  }

  @Nullable
  private static PsiPolyadicExpression getEnclosingLiteralConcatenation(PsiElement element) {
    PsiPolyadicExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PsiPolyadicExpression.class, false, PsiMember.class);
    if (binaryExpression == null) return null;
    PsiClassType stringType = PsiType.getJavaLangString(element.getManager(), element.getResolveScope());
    if (!stringType.equals(binaryExpression.getType())) return null;
    while (true) {
      PsiElement parent = binaryExpression.getParent();
      if (!(parent instanceof PsiPolyadicExpression)) return binaryExpression;
      PsiPolyadicExpression parentBinaryExpression = (PsiPolyadicExpression)parent;
      if (!stringType.equals(parentBinaryExpression.getType())) return binaryExpression;
      binaryExpression = parentBinaryExpression;
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
