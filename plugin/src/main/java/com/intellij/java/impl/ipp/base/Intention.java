/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.base;

import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import consulo.codeEditor.Editor;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.BaseElementAtCaretIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.project.Project;
import consulo.virtualFileSystem.ReadonlyStatusHandler;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class Intention extends BaseElementAtCaretIntentionAction {

  private final PsiElementPredicate predicate;

  /**
   * @noinspection AbstractMethodCallInConstructor, OverridableMethodCallInConstructor
   */
  protected Intention() {
    predicate = getElementPredicate();
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element){
    if (!isWritable(project, element)) {
      return;
    }
    final PsiElement matchingElement = findMatchingElement(element, editor);
    if (matchingElement == null) {
      return;
    }
    processIntention(editor, matchingElement);
  }

  protected abstract void processIntention(@Nonnull PsiElement element);
  
  protected void processIntention(Editor editor, @Nonnull PsiElement element) {
    processIntention(element);
  }

  @Nonnull
  protected abstract PsiElementPredicate getElementPredicate();

  protected static void replaceExpression(@Nonnull String newExpression, @Nonnull PsiExpression expression){
    final Project project = expression.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiExpression newCall = factory.createExpressionFromText(newExpression, expression);
    final PsiElement insertedElement = expression.replace(newCall);
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    codeStyleManager.reformat(insertedElement);
  }

  protected static void replaceExpressionWithNegatedExpression(@Nonnull PsiExpression newExpression, @Nonnull PsiExpression expression){
    final Project project = expression.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiExpression expressionToReplace = expression;
    final String newExpressionText = newExpression.getText();
    final String expString;
    if (BoolUtils.isNegated(expression)) {
      expressionToReplace = BoolUtils.findNegation(expression);
      expString = newExpressionText;
    }
    else if (ComparisonUtils.isComparison(newExpression)) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)newExpression;
      final String negatedComparison = ComparisonUtils.getNegatedComparison(binaryExpression.getOperationTokenType());
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      assert rhs != null;
      expString = lhs.getText() + negatedComparison + rhs.getText();
    }
    else {
      if (ParenthesesUtils.getPrecedence(newExpression) > ParenthesesUtils.PREFIX_PRECEDENCE) {
        expString = "!(" + newExpressionText + ')';
      }
      else {
        expString = '!' + newExpressionText;
      }
    }
    final PsiExpression newCall = factory.createExpressionFromText(expString, expression);
    assert expressionToReplace != null;
    final PsiElement insertedElement = expressionToReplace.replace(newCall);
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    codeStyleManager.reformat(insertedElement);
  }

  protected static void replaceExpressionWithNegatedExpressionString(@Nonnull String newExpression, @Nonnull PsiExpression expression) {
    final Project project = expression.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    PsiExpression expressionToReplace = expression;
    final String expString;
    if (BoolUtils.isNegated(expression)) {
      expressionToReplace = BoolUtils.findNegation(expressionToReplace);
      expString = newExpression;
    }
    else {
      PsiElement parent = expressionToReplace.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        expressionToReplace = (PsiExpression)parent;
        parent = parent.getParent();
      }
      expString = "!(" + newExpression + ')';
    }
    final PsiExpression newCall = factory.createExpressionFromText(expString, expression);
    assert expressionToReplace != null;
    final PsiElement insertedElement = expressionToReplace.replace(newCall);
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    codeStyleManager.reformat(insertedElement);
  }

  protected static void replaceStatement(@NonNls @Nonnull String newStatementText, @NonNls @Nonnull PsiStatement statement) {
    final Project project = statement.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiStatement newStatement = factory.createStatementFromText(newStatementText, statement);
    final PsiElement insertedElement = statement.replace(newStatement);
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    codeStyleManager.reformat(insertedElement);
  }

  protected static void replaceStatementAndShorten(@NonNls @Nonnull String newStatementText, @NonNls @Nonnull PsiStatement statement) {
    final Project project = statement.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiStatement newStatement = factory.createStatementFromText(newStatementText, statement);
    final PsiElement insertedElement = statement.replace(newStatement);
    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    final PsiElement shortenedElement = javaCodeStyleManager.shortenClassReferences(insertedElement);
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    codeStyleManager.reformat(shortenedElement);
  }

  protected static void addStatementBefore(String newStatementText, PsiReturnStatement anchor) {
    final Project project = anchor.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiStatement newStatement = factory.createStatementFromText(newStatementText, anchor);
    final PsiElement addedStatement = anchor.getParent().addBefore(newStatement, anchor);
    CodeStyleManager.getInstance(project).reformat(addedStatement);
  }

  @Nullable
  PsiElement findMatchingElement(@Nullable PsiElement element, Editor editor) {
    while (element != null) {
      if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) {
        break;
      }
      if (predicate instanceof PsiElementEditorPredicate) {
        if (((PsiElementEditorPredicate)predicate).satisfiedBy(element, editor)) {
          return element;
        }
      }
      else if (predicate.satisfiedBy(element)) {
        return element;
      }
      element = element.getParent();
      if (element instanceof PsiFile) {
        break;
      }
    }
    return null;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    return findMatchingElement(element, editor) != null;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static boolean isWritable(Project project, PsiElement element) {
    final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    if (virtualFile == null) {
      return true;
    }
    final ReadonlyStatusHandler readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project);
    final ReadonlyStatusHandler.OperationStatus operationStatus = readonlyStatusHandler.ensureFilesWritable(virtualFile);
    return !operationStatus.hasReadonlyFiles();
  }

  protected String getPrefix() {
    final Class<? extends Intention> aClass = getClass();
    final String name = aClass.getSimpleName();
    final StringBuilder buffer = new StringBuilder(name.length() + 10);
    buffer.append(Character.toLowerCase(name.charAt(0)));
    for (int i = 1; i < name.length(); i++) {
      final char c = name.charAt(i);
      if (Character.isUpperCase(c)) {
        buffer.append('.');
        buffer.append(Character.toLowerCase(c));
      }
      else {
        buffer.append(c);
      }
    }
    return buffer.toString();
  }

  @Override
  @Nonnull
  public String getText() {
    //noinspection UnresolvedPropertyKey
    return IntentionPowerPackBundle.message(getPrefix() + ".name");
  }

  @Nonnull
  public String getFamilyName() {
    //noinspection UnresolvedPropertyKey
    return IntentionPowerPackBundle.defaultableMessage(getPrefix() + ".family.name");
  }
}