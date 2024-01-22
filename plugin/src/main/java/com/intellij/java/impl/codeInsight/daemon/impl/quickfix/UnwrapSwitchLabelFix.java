// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.fix.DeleteSwitchLabelFix;
import com.intellij.java.impl.ig.controlflow.SwitchStatementWithTooFewBranchesInspection.UnwrapSwitchStatementFix;
import com.intellij.java.language.impl.codeInsight.BlockUtils;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.BreakConverter;
import com.siyeh.ig.psiutils.CommentTracker;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Objects;

public class UnwrapSwitchLabelFix implements LocalQuickFix {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Nonnull
  @Override
  public String getFamilyName() {
    return "Remove unreachable branches";
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiExpression label = ObjectUtil.tryCast(descriptor.getStartElement(), PsiExpression.class);
    if (label == null) {
      return;
    }
    PsiSwitchLabelStatementBase labelStatement = PsiImplUtil.getSwitchLabel(label);
    if (labelStatement == null) {
      return;
    }
    PsiSwitchBlock block = labelStatement.getEnclosingSwitchBlock();
    if (block == null) {
      return;
    }
    List<PsiSwitchLabelStatementBase> labels = PsiTreeUtil.getChildrenOfTypeAsList(block.getBody(), PsiSwitchLabelStatementBase.class);
    boolean shouldKeepDefault = block instanceof PsiSwitchExpression &&
        !(labelStatement instanceof PsiSwitchLabeledRuleStatement &&
            ((PsiSwitchLabeledRuleStatement) labelStatement).getBody() instanceof PsiExpressionStatement);
    for (PsiSwitchLabelStatementBase otherLabel : labels) {
      if (otherLabel == labelStatement || (shouldKeepDefault && otherLabel.isDefaultCase())) {
        continue;
      }
      DeleteSwitchLabelFix.deleteLabel(otherLabel);
    }
    for (PsiExpression expression : Objects.requireNonNull(labelStatement.getCaseValues()).getExpressions()) {
      if (expression != label) {
        new CommentTracker().deleteAndRestoreComments(expression);
      }
    }
    tryUnwrap(labelStatement, block);
  }

  public void tryUnwrap(PsiSwitchLabelStatementBase labelStatement, PsiSwitchBlock block) {
    if (block instanceof PsiSwitchStatement) {
      BreakConverter converter = BreakConverter.from(block);
      if (converter == null) {
        return;
      }
      converter.process();
      unwrapStatement(labelStatement, (PsiSwitchStatement) block);
    } else {
      UnwrapSwitchStatementFix.unwrapExpression((PsiSwitchExpression) block);
    }
  }

  private static void unwrapStatement(PsiSwitchLabelStatementBase labelStatement, PsiSwitchStatement statement) {
    PsiCodeBlock block = statement.getBody();
    PsiStatement body =
        labelStatement instanceof PsiSwitchLabeledRuleStatement ? ((PsiSwitchLabeledRuleStatement) labelStatement).getBody() : null;
    if (body == null) {
      new CommentTracker().deleteAndRestoreComments(labelStatement);
    } else if (body instanceof PsiBlockStatement) {
      block = ((PsiBlockStatement) body).getCodeBlock();
    } else {
      new CommentTracker().replaceAndRestoreComments(labelStatement, body);
    }
    PsiCodeBlock parent = ObjectUtil.tryCast(statement.getParent(), PsiCodeBlock.class);
    CommentTracker ct = new CommentTracker();
    if (parent != null && !BlockUtils.containsConflictingDeclarations(Objects.requireNonNull(block), parent)) {
      ct.grabComments(statement);
      ct.markUnchanged(block);
      ct.insertCommentsBefore(statement);
      BlockUtils.inlineCodeBlock(statement, block);
    } else if (block != null) {
      ct.replaceAndRestoreComments(statement, ct.text(block));
    } else {
      ct.deleteAndRestoreComments(statement);
    }
  }
}
