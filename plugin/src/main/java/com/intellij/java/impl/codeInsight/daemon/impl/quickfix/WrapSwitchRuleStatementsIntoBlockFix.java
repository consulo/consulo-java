// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;

import java.util.Objects;

public class WrapSwitchRuleStatementsIntoBlockFix extends BaseIntentionAction implements SyntheticIntentionAction {
  private final PsiSwitchLabeledRuleStatement myRuleStatement;

  public WrapSwitchRuleStatementsIntoBlockFix(PsiSwitchLabeledRuleStatement ruleStatement) {
    myRuleStatement = ruleStatement;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }
    if (!myRuleStatement.isValid()) {
      return false;
    }
    if (myRuleStatement.getBody() instanceof PsiBlockStatement) {
      return false;
    }
    PsiStatement sibling = PsiTreeUtil.getNextSiblingOfType(myRuleStatement, PsiStatement.class);
    if (sibling == null || sibling instanceof PsiSwitchLabelStatementBase) {
      setText("Create block");
    } else {
      setText("Wrap with block");
    }
    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!myRuleStatement.isValid()) {
      return;
    }
    PsiCodeBlock parent = ObjectUtil.tryCast(myRuleStatement.getParent(), PsiCodeBlock.class);
    if (parent == null) {
      return;
    }
    PsiJavaToken rBrace = parent.getRBrace();
    PsiElement[] children = parent.getChildren();
    int index = ArrayUtil.indexOf(children, myRuleStatement);
    assert index >= 0;
    int nextIndex = index + 1;
    while (nextIndex < children.length && !(children[nextIndex] instanceof PsiSwitchLabelStatementBase) && children[nextIndex] != rBrace) {
      nextIndex++;
    }
    if (children[nextIndex - 1] instanceof PsiWhiteSpace) {
      nextIndex--;
    }
    PsiElement oldBody = null;
    if (myRuleStatement.getBody() != null) {
      oldBody = myRuleStatement.getBody().copy();
      myRuleStatement.getBody().delete();
    }
    PsiSwitchLabeledRuleStatement newRule = (PsiSwitchLabeledRuleStatement) JavaPsiFacade.getElementFactory(project).createStatementFromText(
        myRuleStatement.getText() + "{}", myRuleStatement);
    PsiCodeBlock block = ((PsiBlockStatement) Objects.requireNonNull(newRule.getBody())).getCodeBlock();
    if (oldBody != null) {
      block.add(oldBody);
    }
    if (nextIndex > index + 1) {
      PsiElement first = children[index + 1];
      PsiElement last = children[nextIndex - 1];
      block.addRange(first, last);
      parent.deleteChildRange(first, last);
    }
    myRuleStatement.replace(newRule);
  }
}
