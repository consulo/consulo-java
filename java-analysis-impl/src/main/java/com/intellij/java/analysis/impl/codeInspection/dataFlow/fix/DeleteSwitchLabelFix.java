// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.fix;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class DeleteSwitchLabelFix implements LocalQuickFix {
  private final String myName;
  private final boolean myBranch;

  public DeleteSwitchLabelFix(@Nonnull PsiExpression label) {
    myName = label.getText();
    PsiSwitchLabelStatementBase labelStatement = Objects.requireNonNull(PsiImplUtil.getSwitchLabel(label));
    PsiExpressionList values = labelStatement.getCaseValues();
    boolean multiple = values != null && values.getExpressionCount() > 1;
    myBranch = !multiple && shouldRemoveBranch(labelStatement);
  }

  private static boolean shouldRemoveBranch(PsiSwitchLabelStatementBase label) {
    if (label instanceof PsiSwitchLabeledRuleStatement) {
      return true;
    }
    PsiStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(label, PsiStatement.class);
    if (nextStatement instanceof PsiSwitchLabelStatement) {
      return false;
    }
    PsiStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(label, PsiStatement.class);
    return prevStatement == null || !ControlFlowUtils.statementMayCompleteNormally(prevStatement);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Nonnull
  @Override
  public String getName() {
    return myBranch ?
        "Remove switch branch '" + myName + "'" :
        "Remove switch label '" + myName + "'";
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Nonnull
  @Override
  public String getFamilyName() {
    return "Remove switch label";
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiExpression expression = ObjectUtil.tryCast(descriptor.getStartElement(), PsiExpression.class);
    if (expression == null) {
      return;
    }
    PsiSwitchLabelStatementBase label = PsiImplUtil.getSwitchLabel(expression);
    if (label == null) {
      return;
    }
    PsiExpressionList values = label.getCaseValues();
    if (values != null && values.getExpressionCount() == 1) {
      deleteLabel(label);
    } else {
      new CommentTracker().deleteAndRestoreComments(expression);
    }
  }

  public static void deleteLabel(PsiSwitchLabelStatementBase label) {
    if (shouldRemoveBranch(label)) {
      PsiCodeBlock scope = ObjectUtil.tryCast(label.getParent(), PsiCodeBlock.class);
      if (scope == null) {
        return;
      }
      PsiSwitchLabelStatementBase nextLabel = PsiTreeUtil.getNextSiblingOfType(label, PsiSwitchLabelStatementBase.class);
      PsiElement stopAt = nextLabel == null ? scope.getRBrace() : nextLabel;
      while (true) {
        PsiStatement next = PsiTreeUtil.getNextSiblingOfType(nextLabel, PsiStatement.class);
        if (!(next instanceof PsiSwitchLabelStatement)) {
          break;
        }
        nextLabel = (PsiSwitchLabelStatement) next;
      }
      int end = nextLabel == null ? -1 : nextLabel.getTextOffset();
      List<PsiElement> toDelete = new ArrayList<>();
      List<PsiDeclarationStatement> declarations = new ArrayList<>();
      for (PsiElement e = label.getNextSibling(); e != stopAt; e = e.getNextSibling()) {
        if (e instanceof PsiDeclarationStatement && nextLabel != null) {
          PsiDeclarationStatement declaration = (PsiDeclarationStatement) e;
          PsiElement[] elements = declaration.getDeclaredElements();
          boolean declarationIsReused = Stream.of(elements).anyMatch(element ->
              ReferencesSearch.search(element, new LocalSearchScope(scope)).anyMatch(ref -> ref.getElement().getTextOffset() > end));
          if (declarationIsReused) {
            StreamEx.of(elements).select(PsiVariable.class).map(PsiVariable::getInitializer).nonNull().into(toDelete);
            declarations.add(declaration);
            continue;
          }
        }
        toDelete.add(e);
      }
      CommentTracker ct = new CommentTracker();
      toDelete.stream().filter(PsiElement::isValid).forEach(ct::delete);
      for (PsiDeclarationStatement declaration : declarations) {
        scope.addAfter(declaration, nextLabel);
        declaration.delete();
      }
      ct.insertCommentsBefore(label);
    }
    new CommentTracker().deleteAndRestoreComments(label);
  }
}
