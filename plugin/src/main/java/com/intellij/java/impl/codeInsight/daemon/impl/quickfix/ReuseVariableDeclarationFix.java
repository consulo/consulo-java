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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.language.impl.psi.scope.processor.VariablesNotProcessor;
import com.intellij.java.language.impl.psi.scope.util.PsiScopesUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.codeEditor.Editor;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.java.analysis.impl.localize.JavaQuickFixLocalize;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.Comparing;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author cdr
 * Date: Nov 20, 2002
 */
public class ReuseVariableDeclarationFix implements SyntheticIntentionAction {
  private final PsiLocalVariable myVariable;

  public ReuseVariableDeclarationFix(final PsiLocalVariable variable) {
    this.myVariable = variable;
  }

  @Override
  @Nonnull
  public LocalizeValue getText() {
    return JavaQuickFixLocalize.reuseVariableDeclarationText(myVariable.getName());
  }

  @Override
  public boolean isAvailable(@Nonnull final Project project, final Editor editor, final PsiFile file) {
    if (myVariable == null || !myVariable.isValid()) {
      return false;
    }
    final PsiVariable previousVariable = findPreviousVariable();
    return previousVariable != null &&
           Comparing.equal(previousVariable.getType(), myVariable.getType()) &&
           myVariable.getManager().isInProject(myVariable);
  }

  @Override
  public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiVariable refVariable = findPreviousVariable();
    if (refVariable == null) return;

    if (!CodeInsightUtil.preparePsiElementsForWrite(myVariable, refVariable)) return;

    final PsiExpression initializer = myVariable.getInitializer();
    if (initializer == null) {
      myVariable.delete();
      return;
    }

    PsiUtil.setModifierProperty(refVariable, PsiModifier.FINAL, false);
    final PsiElementFactory factory = JavaPsiFacade.getInstance(myVariable.getProject()).getElementFactory();
    final PsiElement statement = factory.createStatementFromText(myVariable.getName() + " = " + initializer.getText() + ";", null);
    myVariable.getParent().replace(statement);
  }

  @Nullable
  private PsiVariable findPreviousVariable() {
    PsiElement scope = myVariable.getParent();
    while (scope != null) {
      if (scope instanceof PsiFile || scope instanceof PsiMethod || scope instanceof PsiClassInitializer) break;
      scope = scope.getParent();
    }
    if (scope == null) return null;

    PsiIdentifier nameIdentifier = myVariable.getNameIdentifier();
    if (nameIdentifier == null) {
      return null;
    }

    final VariablesNotProcessor processor = new VariablesNotProcessor(myVariable, false);
    PsiScopesUtil.treeWalkUp(processor, nameIdentifier, scope);
    return processor.size() > 0 ? processor.getResult(0) : null;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
